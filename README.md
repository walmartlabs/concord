# Concord

![](https://img.shields.io/maven-central/v/com.walmartlabs.concord/parent.svg)

- Website: https://concord.walmartlabs.com
- [Installation guide](https://concord.walmartlabs.com/docs/getting-started/installation.html)
- [Core Plugins](./plugins)
- [Community Plugins](https://github.com/walmartlabs/concord-plugins/)

![](console2/public/images/concord.svg)

Concord is a workflow server. It connects different systems through scenarios and plugins that users create.

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

Install these dependencies:
- [Git](https://git-scm.com/) 2.18 or later.
- [Java 25](https://adoptium.net/).
- [Docker Community Edition](https://www.docker.com/community-edition).
- [Docker Buildx](https://docs.docker.com/build/buildx/install/).
- Optional: [NodeJS and NPM](https://nodejs.org/en/download/) (Node 24 LTS).

```shell
git clone https://github.com/walmartlabs/concord.git
cd concord
./mvnw clean install -DskipTests
```

Maven profiles:

- `docker`: Build Docker images.
- `it`: Run integration tests.
- `jdk25-aarch64`: Build Docker images for aarch64.

You can use more than one profile in a command:

```
./mvnw clean install -Pdocker -Pit -Pjdk25-aarch64
```

## Console

See the [console2/README.md](./console2/README.md) file.
```shell
cd ./console2
npm ci # Install dependencies
```

Start the console in development mode:
```shell
npm run start
```

## Integration tests

### Prerequisites

Before you run integration tests, make sure that these requirements are met:

- Git 2.18 or later is available.
- Docker is available. The `it/server` module with `-Pdocker` requires privileged containers.
- Ansible 2.6.0 or later is installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html).
- The Python module `requests` is installed.
  To install it, use `pip install requests` or the system package manager.
- Java is available in `$PATH` as `java`.
- [Chrome WebDriver](http://chromedriver.chromium.org/) is available in `$PATH`.

Server-mode integration tests can use an agent that you manage locally.
For these tests, configure a Docker endpoint for the agent.
The `it/server` module with `-Pdocker` manages a privileged Docker-in-Docker daemon.

### Running tests

Integration tests do not run by default. To run them, use the `it` profile:

```shell
./mvnw verify -Pit
```

This command runs integration tests against the local server and agent.
Tasks that use Docker require a Docker endpoint for the agent.
The `it/server -Pdocker` configuration uses a managed Docker-in-Docker daemon instead.
See [the server IT instructions](./it/server/README.md).

To start and stop the server and agent automatically with Docker, use the `docker` profile:
```shell
./mvnw verify -Pit -Pdocker
```

To run UI integration tests in an IDE with the UI in development mode:

1. Start the UI with `cd console2 && npm start`.
2. Set the environment variable `IT_CONSOLE_BASE_URL=http://localhost:3000` before you run UI tests.

## Repository Docs

For development instructions for this repository, see these documents:

- [Integration tests](./it/README.md)
- [Development notes](./NOTES.md)
- [Server README](./server/README.md)
- [Console UI README](./console2/README.md)
- [Agent operator README](./agent-operator/README.md)

## Examples

See the [examples](examples) directory.

## How To Release New Versions

1. Run the standard Maven release procedure:

   ```
   $ ./mvnw release:prepare release:perform
   ```

2. Update the `CHANGELOG.md` file.
3. Commit the `CHANGELOG.md` file:

   ```
   $ git add CHANGELOG.md
   $ git commit -m 'update changelog'
   ```

4. Push the new tag. Then push the master branch:

   ```
   $ git push origin RELEASE_TAG
   $ git push origin master
   ```

5. Build the Docker images. Then push the images:

   ```
   $ git checkout RELEASE_TAG
   $ gh workflow run docker-multiarch.yml --ref master -f ref=RELEASE_TAG -f docker_tag=RELEASE_TAG -f docker_namespace=walmartlabs
   ```

6. Synchronize the release with [Sonatype](https://oss.sonatype.org/).
7. Check the Central repository to see if synchronization is complete:

   ```
   https://repo.maven.apache.org/maven2/com/walmartlabs/concord/parent/RELEASE_TAG
   ```

8. When synchronization is complete, push the `latest` Docker images:

   ```
   $ gh workflow run docker-multiarch.yml --ref master -f ref=RELEASE_TAG -f docker_tag=latest -f docker_namespace=walmartlabs
   ```

## Development Notes

See [NOTES.md](NOTES.md).
