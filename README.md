# Concord

![](https://img.shields.io/maven-central/v/com.walmartlabs.concord/parent.svg)

## Build Dependencies

- [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Docker Community Edition](https://www.docker.com/community-edition)
- (Optional) [NodeJS and NPM](https://nodejs.org/en/download/) (Node 8 or greater)

## Building

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

```shell
cd ./console2
npm install # Install dependencies
```

Verify that the `proxy` property in [console2/package.json](console2/package.json)
is set to the Server's API address.

Start the console in dev mode by running:
```shell
npm run start
```

### Integration tests

#### Prerequisites

Prerequisites:

- Git 2.3+
- Docker, listening on `tcp://127.0.0.1:2375`;
- Ansible 2.6.0+ must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- `ujson` python module is required. It can be installed using `pip install ujson`
or a system package manager;
- `requests` python module is required. It can be installed using `pip install requests`
or a system package manager;
- Java must be available in `$PATH` as `java`.

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

## Examples

See the [examples](examples) directory.
