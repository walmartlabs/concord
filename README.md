# Concord

## Build Dependencies

- [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- [Docker Community Edition](https://www.docker.com/community-edition)
- [NodeJS and NPM](https://nodejs.org/en/download/) (Node 8 or greater)

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

Console can be built and ran separately from within the console
directory.

```shell
cd ./console2
npm install # Install dependencies
```

In order for the console to successfully startup the proxy property
in [console/package.json](console/package.json) needs to point to a
running concord server instance.

```shell
npm run start # Starts the Concord Console proxied to concord server
```

### Integration tests

#### Prerequisites

Prerequisites:

- Docker, listening on `tcp://127.0.0.1:2375`;
- Ansible 2.5.0+ must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- `ujson` python module. It can be installed using `pip install ujson`
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
