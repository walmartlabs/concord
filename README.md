# Concord

![](https://img.shields.io/maven-central/v/com.walmartlabs.concord/parent.svg)

- Website: https://concord.walmartlabs.com
- [Installation guide](https://concord.walmartlabs.com/docs/getting-started/installation.html)
- [Core Plugins](./plugins)
- [Community Plugins](https://github.com/walmartlabs/concord-plugins/)

![](console2/public/images/concord.svg)

Concord is a workflow server. It is the orchestration engine that connects
different systems together using scenarios and plugins created by users.

- [Building](#building)
- [Console](#console)
- [Integration tests](#integration-tests)
  * [Prerequisites](#prerequisites)
  * [Running tests](#running-tests)
- [Repository Docs](#repository-docs)
- [Examples](#examples)
- [How To Release New Versions](#how-to-release-new-versions)
- [Development Notes](#development-notes)

## Building

Dependencies:
- [Git](https://git-scm.com/) 2.18+
- [Java 17](https://adoptium.net/)
- [Docker Community Edition](https://www.docker.com/community-edition)
- [Docker Buildx](https://docs.docker.com/build/buildx/install/)
- (Optional) [NodeJS and NPM](https://nodejs.org/en/download/) (Node 24 LTS)

```shell
git clone https://github.com/walmartlabs/concord.git
cd concord
./mvnw clean install -DskipTests
```

Available Maven profiles:

- `docker` - build Docker images;
- `it` - run integration tests;
- `jdk17-aarch64` - use a different JDK version for building artifacts and Docker images.

Profiles can be combined, e.g.

```
./mvnw clean install -Pdocker -Pit -Pjdk17-aarch64
```

## Console

See the [console2/README.md](./console2/README.md) file.
```shell
cd ./console2
npm ci # Install dependencies
```

Start the console in dev mode by running:
```shell
npm run start
```

## Integration tests

### Prerequisites

Prerequisites:

- Git 2.18+
- Docker, listening on `tcp://127.0.0.1:2375`;
- Ansible 2.6.0+ must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- `requests` python module is required. It can be installed by using `pip install requests`
  or the system package manager;
- Java must be available in `$PATH` as `java`;
- [Chrome WebDriver](http://chromedriver.chromium.org/) available in `$PATH`.

### Running tests

Integration tests are disabled by default. Use the `it` profile to enable them:

```shell
./mvnw verify -Pit
```

This will run ITs agains the locally running server and the agent.
To automatically start and stop the server and the agent using docker, use the
`docker` profile:

```shell
./mvnw verify -Pit -Pdocker
```

To run UI ITs in an IDE using the UI's dev mode:
- start the UI's dev mode with `cd console2 && npm start`;
- set up `IT_CONSOLE_BASE_URL=http://localhost:3000` environment variable before running
any UI tests.

## Repository Docs

For repo-specific development entrypoints, see:

- [Integration tests](./it/README.md)
- [Development notes](./NOTES.md)
- [Server README](./server/README.md)
- [Console UI README](./console2/README.md)
- [Agent operator README](./agent-operator/README.md)

## Examples

See the [examples](examples) directory.

## How To Release New Versions

- perform a regular Maven release:
  ```
  $ ./mvnw release:prepare release:perform
  ```
- update and commit the CHANGELOG.md file
  ```
  $ git add CHANGELOG.md
  $ git commit -m 'update changelog'
  ```
- push the new tag and the master branch:
  ```
  $ git push origin RELEASE_TAG
  $ git push origin master
  ```
- ensure the published Docker base images are current. They are rebuilt weekly
  by `docker-base-images.yml` as `latest` plus an immutable weekly tag, but they
  can also be refreshed manually:
  ```
  $ gh workflow run docker-base-images.yml --ref master -f ref=master -f docker_tag=latest -f docker_namespace=walmartlabs
  ```
- build and push the Docker application images. The workflow resolves the
  published `concord-base` and `concord-ansible` images from `base_docker_tag`
  to digests before building:
  ```
  $ git checkout RELEASE_TAG
  $ gh workflow run docker-multiarch.yml --ref master -f ref=RELEASE_TAG -f docker_tag=RELEASE_TAG -f docker_namespace=walmartlabs -f base_docker_tag=latest
  ```
- sync to [Sonatype](https://oss.sonatype.org/);
- check the Central repository if the sync is complete:
  ```
  https://repo.maven.apache.org/maven2/com/walmartlabs/concord/parent/RELEASE_TAG
  ```
- once the sync is complete, push the `latest` Docker images:
  ```
  $ gh workflow run docker-multiarch.yml --ref master -f ref=RELEASE_TAG -f docker_tag=latest -f docker_namespace=walmartlabs -f base_docker_tag=latest
  ```

## Development Notes

See [NOTES.md](NOTES.md).
