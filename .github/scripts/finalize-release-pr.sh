#!/usr/bin/env bash

set -euo pipefail

release_version="${RELEASE_VERSION:?RELEASE_VERSION is required}"
next_development_version="${NEXT_DEVELOPMENT_VERSION:?NEXT_DEVELOPMENT_VERSION is required}"
changelog_branch="${CHANGELOG_BRANCH:?CHANGELOG_BRANCH is required}"
release_date="${RELEASE_DATE:-}"

if ! [[ "${release_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Release version must look like 2.41.0, got '${release_version}'." >&2
    exit 1
fi

if ! [[ "${next_development_version}" =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]]; then
    echo "Next development version must look like 2.41.1-SNAPSHOT, got '${next_development_version}'." >&2
    exit 1
fi

if ! git check-ref-format "refs/heads/${changelog_branch}"; then
    echo "Invalid changelog branch name '${changelog_branch}'." >&2
    exit 1
fi

if [[ "${changelog_branch}" == "master" ]]; then
    echo "Refusing to finalize the protected master branch." >&2
    exit 1
fi

if [[ -z "${release_date}" ]]; then
    release_date="$(date -u +%Y-%m-%d)"
elif ! [[ "${release_date}" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]]; then
    echo "Release date must use YYYY-MM-DD format, got '${release_date}'." >&2
    exit 1
fi

if git ls-remote --exit-code --tags origin "refs/tags/${release_version}" >/dev/null 2>&1; then
    echo "Release tag '${release_version}' already exists on origin." >&2
    exit 1
fi

if git rev-parse --quiet --verify "refs/tags/${release_version}" >/dev/null; then
    git tag -d "${release_version}"
fi

git fetch --prune origin \
    "+refs/heads/master:refs/remotes/origin/master" \
    "+refs/heads/${changelog_branch}:refs/remotes/origin/${changelog_branch}"

git checkout -B "${changelog_branch}" "refs/remotes/origin/${changelog_branch}"
git rebase refs/remotes/origin/master

mapfile -t changed_files < <(git diff --name-only refs/remotes/origin/master...HEAD)
if ((${#changed_files[@]} == 0)); then
    echo "Branch '${changelog_branch}' has no changes relative to origin/master." >&2
    exit 1
fi

if ((${#changed_files[@]} != 1)) || [[ "${changed_files[0]}" != "CHANGELOG.md" ]]; then
    echo "The existing PR branch must contain changelog-only changes before finalizing." >&2
    printf 'Changed files:\n' >&2
    printf '  %s\n' "${changed_files[@]}" >&2
    exit 1
fi

git reset --soft refs/remotes/origin/master
git commit -m "update changelog"

tbd_heading="## [${release_version}] - TBD"
dated_heading="## [${release_version}] - ${release_date}"
tbd_count="$(grep -Fxc -- "${tbd_heading}" CHANGELOG.md || true)"

if [[ "${tbd_count}" != "1" ]]; then
    if grep -Fq -- "## [${release_version}] - " CHANGELOG.md; then
        echo "CHANGELOG.md contains a heading for ${release_version}, but it is not '${tbd_heading}'." >&2
    else
        echo "CHANGELOG.md does not contain '${tbd_heading}'." >&2
    fi
    exit 1
fi

tmp_changelog="$(mktemp)"
awk -v from="${tbd_heading}" -v to="${dated_heading}" '
    $0 == from {
        print to
        next
    }
    {
        print
    }
' CHANGELOG.md > "${tmp_changelog}"
mv "${tmp_changelog}" CHANGELOG.md

git add CHANGELOG.md
git commit --amend --no-edit

./mvnw -B release:prepare \
    -DreleaseVersion="${release_version}" \
    -Dtag="${release_version}" \
    -DdevelopmentVersion="${next_development_version}" \
    -Darguments="--batch-mode -DskipTests"

while IFS= read -r -d '' scratch_file; do
    printf 'Removing release scratch file %s\n' "${scratch_file}"
    rm -f "${scratch_file}"
done < <(find . -path ./.git -prune -o -type f \( \
    -name 'pom.xml.releaseBackup' -o \
    -name 'pom.xml.next' -o \
    -name 'pom.xml.tag' -o \
    -name 'pom.xml.branch' \
\) -print0)
rm -f release.properties

if git rev-parse --quiet --verify "refs/tags/${release_version}" >/dev/null; then
    git tag -d "${release_version}"
fi

if [[ -n "$(git status --porcelain)" ]]; then
    echo "Working tree is not clean after release preparation:" >&2
    git status --short >&2
    exit 1
fi

git push --force-with-lease="refs/heads/${changelog_branch}" origin "HEAD:refs/heads/${changelog_branch}"

{
    echo "## Release PR finalized"
    echo
    echo "- Branch: \`${changelog_branch}\`"
    echo "- Release version: \`${release_version}\`"
    echo "- Release date: \`${release_date}\`"
    echo
    echo "**Merge requirement:** merge the final PR with GitHub \`Rebase and merge\`."
    echo "Do not squash merge it. The publish workflow tags the preserved"
    echo "\`[maven-release-plugin] prepare release ${release_version}\` commit on \`master\`."
} >> "${GITHUB_STEP_SUMMARY:-/dev/null}"

echo "Finalized '${changelog_branch}' for ${release_version}."
echo "Merge the PR with GitHub 'Rebase and merge'; do not squash merge it."
