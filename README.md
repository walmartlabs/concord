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
- [Examples](#examples)
- [How To Release New Versions](#how-to-release-new-versions)
- [Development Notes](#development-notes)

## Building

Dependencies:
- [Git](https://git-scm.com/) 2.18+
- [Java 17](https://adoptium.net/)
- [Docker Community Edition](https://www.docker.com/community-edition)
- [Docker Buildx](https://docs.docker.com/build/buildx/install/)
- (Optional) [NodeJS and NPM](https://nodejs.org/en/download/) (Node 20 or greater)

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

- Git 2.3+
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

## Examples

See the [examples](examples) directory.

## How To Release New Versions

- perform a regular Maven release:
  ```
  $ ./mvnw release:prepare release:perform
  ```
- push the new tag:
  ```
  $ git push origin RELEASE_TAG
  ```
- sync to [Central](https://central.sonatype.com/);
- build and push the Docker images:
  ```
  $ git checkout RELEASE_TAG
  $ ./mvnw -f docker-images clean package -Pdocker
  $ ./docker-images/push.sh RELEASE_TAG
  ```

## Development Notes

See [NOTES.md](NOTES.md).
