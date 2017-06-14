# Concord
[![Build Status](https://ci.walmart.com/buildStatus/icon?job=concord)](https://ci.walmart.com/job/concord/)

## Prerequisites

Prerequisites:
- Docker;
- Ansible must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- Java must be available in `$PATH` as `java`.

## Building

```
git clone ... 
cd concord
./mvnw clean install -DskipTests
```

### Docker images

You can build docker images using this commands:

```
./mvnw clean install -DskipTests -Pdocker
```

### Integration tests


Integration tests are disabled by default. Use the `it` profile to enable them:

```
./mvnw verify -Pit
```

This will run ITs agains the locally running server and the agent.
To automatically start and stop the server and the agent using docker, use the
`docker` profile:

```
./mvnw verify -Pit -Pdocker -Ddocker.host.addr=172.17.0.1
```

The `docker.host.addr` value must be set to the IP address of `docker0` interface.

## Documentation

See [the website](http://concord.walmart.com).

## Examples

See the [examples](docs/examples) directory.

