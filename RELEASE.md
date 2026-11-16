# Release Guide

Concord releases are coordinated through two manual GitHub Actions workflows. The
`finalize-release-pr` workflow prepares the changelog PR as the release PR. The
`publish-release` workflow tags the preserved Maven release commit and publishes the release
artifacts.

## 1. Prepare the Changelog PR

Create the standard changelog PR branch with the target release heading in `CHANGELOG.md`:

```markdown
## [2.41.0] - TBD
```

Before finalization, ensure that the branch modifies only `CHANGELOG.md`.

## 2. Finalize the Release PR

Run the `finalize-release-pr` workflow from `master`. Provide the following inputs:

- `release_version`, for example `2.41.0`
- `next_development_version`, for example `2.41.1-SNAPSHOT`
- `changelog_branch`, the existing changelog PR branch
- `release_date`, optional; empty uses the current UTC date

The workflow performs the following release preparation steps:

- rebases the branch onto `origin/master`
- squashes the existing changelog commits into `update changelog`
- updates the release heading with the release date
- runs `release:prepare`
- removes the local release tag
- force-pushes the PR branch

The final release PR should contain these commits:

- `update changelog`
- `[maven-release-plugin] prepare release <release_version>`
- `[maven-release-plugin] prepare for next development iteration`

## 3. Review and Merge

Review the final release PR through the standard approval process.

Merge it with GitHub `Rebase and merge`. Do not squash merge the release PR. The publish workflow
searches `master` for the preserved
`[maven-release-plugin] prepare release <release_version>` commit and tags that commit. A squash
merge removes that commit and prevents publication.

## 4. Publish the Release

After the final PR has been rebase-merged, run the `publish-release` workflow from `master`. Provide
the following inputs:

- `release_version`, for example `2.41.0`
- `docker_namespace`, normally `walmartlabs`
- `auto_publish_central`, normally `true`

The workflow checks out `master`, finds the preserved Maven release commit, creates and pushes the
annotated release tag, imports the publisher signing key, runs `release:perform`, creates the
GitHub Release, and dispatches `docker-multiarch.yml` with the release tag as both `ref` and
`docker_tag`.

When `auto_publish_central` is `true`, the workflow passes `autoPublish=true`, waits for the
published state, creates the GitHub Release, and triggers Docker image builds.

When `auto_publish_central` is `false`, the workflow uploads the Central deployment and waits for
validation only. It stops before creating the GitHub Release or triggering Docker. Finish
publishing in Central Portal before creating the GitHub Release or building Docker images.

## 5. Monitor Docker Image Publication

After `publish-release` dispatches `docker-multiarch.yml`, watch that workflow until it publishes
the multi-arch Docker images for the release tag.

## Signing and Central Secrets

The publish workflow uses one GitHub Environment per publisher. The environment name must be
`release-${github.actor}`, for example `release-ibodrov`. Configure each publisher environment
with:

- `GPG_KEY`: ASCII-armored private signing subkey, or a base64-encoded private key export
- `GPG_PASSPHRASE`: passphrase for the signing key
- `CENTRAL_USERNAME`: Central Portal user token username
- `CENTRAL_PASSWORD`: Central Portal user token password

Use dedicated, revocable signing subkeys for CI. The workflow rejects reruns where `github.actor`
differs from `github.triggering_actor`; start a new workflow run rather than rerunning another
publisher's release job.
