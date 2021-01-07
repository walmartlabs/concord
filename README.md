# Concord

# Test

![](https://img.shields.io/maven-central/v/com.walmartlabs.concord/parent.svg)

- Website: https://concord.walmartlabs.com
- [Installation guide](https://concord.walmartlabs.com/docs/getting-started/installation.html)
- [Core Plugins](./plugins)
- [Community Plugins](https://github.com/walmartlabs/concord-plugins/)

![](console2/public/images/concord.svg)

Concord is a workflow server. It is the orchestration engine that connects
different systems together using scenarios and plugins created by users.

## Building

Dependencies:
- [Git](https://git-scm.com/) 2.18+
- [Java 8](https://adoptopenjdk.net/)
- [Docker Community Edition](https://www.docker.com/community-edition)
- (Optional) [NodeJS and NPM](https://nodejs.org/en/download/) (Node 10 or greater)

```shell
git clone ...
cd concord
./mvnw clean install -DskipTests
```

### Docker Images

You can build docker images using this commands:

```shell
./mvnw clean install -DskipTests -Pdocker
```

### Console

See the [console2/README.md](./console2/README.md) file.
```shell
cd ./console2
npm install # Install dependencies
```



Start the console in dev mode by running:
```shell
npm run start
```

### Java 11

Use the `jdk11` profile:

```
./mvnw clean install -DskipTests -Pdocker -Pjdk11
```

This command builds binaries and Docker images using JDK 11.

### Integration tests

#### Prerequisites

Prerequisites:

- Git 2.3+
- Docker, listening on `tcp://127.0.0.1:2375`;
- Ansible 2.6.0+ must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- `requests` python module is required. It can be installed by using `pip install requests`
  or the system package manager;
- Java must be available in `$PATH` as `java`;
- [Chrome WebDriver](http://chromedriver.chromium.org/) available in `$PATH`.

#### Running tests

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

- perform the regular Maven release:
  ```
  $ ./mvnw release:prepare release:perform
  ```
- push the tags:
  ```
  $ git push --tags
  ```
- sync to Central;
- build and push the Docker images:
  ```
  $ git checkout RELEASE_TAG
  $ ./mvnw -f docker-images clean package -Pdocker
  $ ./docker-images/push.sh RELEASE_TAG
  ```
