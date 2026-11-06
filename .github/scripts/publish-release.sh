#!/usr/bin/env bash

set -euo pipefail

release_version="${RELEASE_VERSION:?RELEASE_VERSION is required}"
docker_namespace="${DOCKER_NAMESPACE:-walmartlabs}"
auto_publish_central="${AUTO_PUBLISH_CENTRAL:-false}"
gpg_private_key="${GPG_PRIVATE_KEY:?GPG_PRIVATE_KEY is required}"
gpg_passphrase="${GPG_PASSPHRASE:?GPG_PASSPHRASE is required}"
central_username="${CENTRAL_USERNAME:?CENTRAL_USERNAME is required}"
central_password="${CENTRAL_PASSWORD:?CENTRAL_PASSWORD is required}"

if ! [[ "${release_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Release version must look like 2.41.0, got '${release_version}'." >&2
    exit 1
fi

if [[ -z "${docker_namespace}" || "${docker_namespace}" == */* ]]; then
    echo "Docker namespace must be a Docker Hub namespace like 'walmartlabs'." >&2
    exit 1
fi

case "${auto_publish_central}" in
    true)
        central_wait_until="PUBLISHED"
        ;;
    false)
        central_wait_until="VALIDATED"
        ;;
    *)
        echo "AUTO_PUBLISH_CENTRAL must be 'true' or 'false', got '${auto_publish_central}'." >&2
        exit 1
        ;;
esac

echo "::add-mask::${gpg_passphrase}"
echo "::add-mask::${central_password}"

git fetch --prune --tags origin "+refs/heads/master:refs/remotes/origin/master"
git checkout -B master refs/remotes/origin/master

prepare_subject="[maven-release-plugin] prepare release ${release_version}"
mapfile -t candidate_commits < <(
    git log --format='%H' --fixed-strings --grep="${prepare_subject}" refs/remotes/origin/master
)

release_commits=()
for commit in "${candidate_commits[@]}"; do
    if [[ "$(git show -s --format='%s' "${commit}")" == "${prepare_subject}" ]]; then
        release_commits+=("${commit}")
    fi
done

if ((${#release_commits[@]} != 1)); then
    echo "Expected exactly one '${prepare_subject}' commit on master, found ${#release_commits[@]}." >&2
    echo "If the final release PR was squash-merged, rerun the finalize workflow and merge with Rebase and merge." >&2
    exit 1
fi

release_commit="${release_commits[0]}"
echo "Release commit: ${release_commit}"

if git ls-remote --exit-code --tags origin "refs/tags/${release_version}" >/dev/null 2>&1; then
    git fetch --force origin "refs/tags/${release_version}:refs/tags/${release_version}"
    tag_commit="$(git rev-parse "refs/tags/${release_version}^{commit}")"
    if [[ "${tag_commit}" != "${release_commit}" ]]; then
        echo "Remote tag '${release_version}' points to ${tag_commit}, expected ${release_commit}." >&2
        exit 1
    fi
    echo "Release tag '${release_version}' already exists at the expected commit."
else
    if git rev-parse --quiet --verify "refs/tags/${release_version}" >/dev/null; then
        git tag -d "${release_version}"
    fi
    git tag -a "${release_version}" "${release_commit}" -m "Release ${release_version}"
    git push origin "refs/tags/${release_version}:refs/tags/${release_version}"
fi

runner_temp="${RUNNER_TEMP:-$(mktemp -d)}"
settings_file="${runner_temp}/release-settings.xml"
key_file="${runner_temp}/release-gpg-key.asc"
export GNUPGHOME="${runner_temp}/gnupg"
mkdir -p "${GNUPGHOME}"
chmod 700 "${GNUPGHOME}"

cat > "${GNUPGHOME}/gpg-agent.conf" <<'EOF'
allow-loopback-pinentry
EOF

cat > "${GNUPGHOME}/gpg.conf" <<'EOF'
batch
no-tty
pinentry-mode loopback
EOF

xml_escape() {
    local value="$1"
    value="${value//&/&amp;}"
    value="${value//</&lt;}"
    value="${value//>/&gt;}"
    value="${value//\"/&quot;}"
    value="${value//\'/&apos;}"
    printf '%s' "${value}"
}

cat > "${settings_file}" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>central</id>
            <username>$(xml_escape "${central_username}")</username>
            <password>$(xml_escape "${central_password}")</password>
        </server>
        <server>
            <id>gpg.passphrase</id>
            <passphrase>$(xml_escape "${gpg_passphrase}")</passphrase>
        </server>
    </servers>
</settings>
EOF
chmod 600 "${settings_file}"

if [[ "${gpg_private_key}" == *"BEGIN PGP PRIVATE KEY BLOCK"* ]]; then
    printf '%s' "${gpg_private_key}" > "${key_file}"
else
    printf '%s' "${gpg_private_key}" | base64 --decode > "${key_file}"
fi

gpg --batch --import "${key_file}"
rm -f "${key_file}"
gpgconf --kill gpg-agent || true

gpg_fingerprint="$(gpg --batch --list-secret-keys --with-colons | awk -F: '$1 == "fpr" { print $10; exit }')"
if [[ -z "${gpg_fingerprint}" ]]; then
    echo "No GPG secret key was imported." >&2
    exit 1
fi

printf '%s:6:\n' "${gpg_fingerprint}" | gpg --batch --import-ownertrust
echo "Imported release signing key ${gpg_fingerprint}."

rm -rf target/checkout
repo_dir="$(pwd -P)"
release_arguments=(
    --batch-mode
    -s "${settings_file}"
    -DskipTests
    -Pconcord-release
    "-Dgpg.keyname=${gpg_fingerprint}"
    -Dgpg.passphraseServerId=gpg.passphrase
    "-DautoPublish=${auto_publish_central}"
    "-DwaitUntil=${central_wait_until}"
)

./mvnw -B -s "${settings_file}" release:perform \
    -DconnectionUrl="scm:git:file://${repo_dir}" \
    -Dtag="${release_version}" \
    -DlocalCheckout=true \
    "-Darguments=${release_arguments[*]}"

if [[ "${auto_publish_central}" == "false" ]]; then
    {
        echo "## Release uploaded"
        echo
        echo "- Release version: \`${release_version}\`"
        echo "- Tagged commit: \`${release_commit}\`"
        echo "- Central auto-publish: \`false\`"
        echo
        echo "Central upload waited for validation only."
        echo "Publish the deployment manually in Central Portal before creating the GitHub Release"
        echo "or building Docker images."
    } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

    echo "Central upload validated for ${release_version}."
    echo "Publish it manually in Central Portal before creating the GitHub Release or Docker images."
    exit 0
fi

if gh release view "${release_version}" >/dev/null 2>&1; then
    echo "GitHub Release '${release_version}' already exists."
else
    gh release create "${release_version}" \
        --verify-tag \
        --title "${release_version}" \
        --generate-notes
fi

gh workflow run docker-multiarch.yml \
    --ref master \
    -f "ref=${release_version}" \
    -f "docker_tag=${release_version}" \
    -f "docker_namespace=${docker_namespace}"

{
    echo "## Release published"
    echo
    echo "- Release version: \`${release_version}\`"
    echo "- Tagged commit: \`${release_commit}\`"
    echo "- Central auto-publish: \`${auto_publish_central}\`"
    echo "- Docker namespace: \`${docker_namespace}\`"
    echo
    echo "Central publishing waited for the published state."
    echo
    echo "Triggered \`docker-multiarch.yml\` for tag \`${release_version}\`."
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

echo "Publish workflow completed for ${release_version}."
