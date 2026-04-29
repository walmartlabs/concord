# Release Guide

Concord releases use two manual GitHub Actions workflows. The first workflow turns the changelog PR
into the final release PR. The second workflow tags the preserved Maven release commit and
publishes the release.

## 1. Start With A Changelog PR

Prepare the normal changelog PR branch with the target release heading in `CHANGELOG.md`:

```markdown
## [2.41.0] - TBD
```

Keep the branch changelog-only before finalizing it.

## 2. Finalize The Release PR

Run the `finalize-release-pr` workflow from `master` with:

- `release_version`, for example `2.41.0`
- `next_development_version`, for example `2.41.1-SNAPSHOT`
- `changelog_branch`, the existing changelog PR branch
- `release_date`, optional; empty uses the current UTC date

The workflow rebases the branch onto `origin/master`, squashes the existing changelog commits into
`update changelog`, dates the changelog heading, runs `release:prepare`, deletes the local release
tag, and force-pushes the PR branch.

Expected final PR commits:

- `update changelog`
- `[maven-release-plugin] prepare release <release_version>`
- `[maven-release-plugin] prepare for next development iteration`

## 3. Review And Merge

Review the final release PR normally.

Merge it with GitHub `Rebase and merge`.

Do not squash merge the release PR. The publish workflow searches `master` for the preserved
`[maven-release-plugin] prepare release <release_version>` commit and tags that commit. A squash
merge removes that commit and blocks publishing.

## 4. Publish The Release

After the final PR is rebase-merged, run the `publish-release` workflow from `master` with:

- `release_version`, for example `2.41.0`
- `docker_namespace`, normally `walmartlabs`
- `auto_publish_central`, normally `true`

The workflow checks out `master`, finds the preserved Maven release commit, creates and pushes the
annotated release tag, imports the publisher signing key, runs `release:perform`, creates the
GitHub Release, and dispatches `docker-multiarch.yml` with the release tag as both `ref` and
`docker_tag`.

If `auto_publish_central` is `true`, the workflow passes `autoPublish=true`, waits for the
published state, creates the GitHub Release, and triggers Docker image builds.

If `auto_publish_central` is `false`, the workflow uploads the Central deployment and waits for
validation only. It stops before creating the GitHub Release or triggering Docker. Finish
publishing in Central Portal before creating the GitHub Release or building Docker images.

## 5. Watch Docker

After `publish-release` dispatches `docker-multiarch.yml`, watch that workflow until it publishes
the multi-arch Docker images for the release tag.

## Signing And Central Secrets

The publish workflow uses one GitHub Environment per publisher. The environment name is
`release-${github.actor}`, for example `release-ibodrov`. Configure each publisher environment
with:

- `GPG_KEY`: ASCII-armored private signing subkey, or a base64-encoded private key export
- `GPG_PASSPHRASE`: passphrase for the signing key
- `CENTRAL_USERNAME`: Central Portal user token username
- `CENTRAL_PASSWORD`: Central Portal user token password

Use dedicated, revocable signing subkeys for CI. The workflow rejects reruns where `github.actor`
differs from `github.triggering_actor`; start a new workflow run instead of rerunning another
publisher's release job.
