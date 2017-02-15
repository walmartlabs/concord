TLDR: `mvn clean install -Pit -Pdocker`

# Building

```
git clone ... 
cd concord
./mvnw clean install -DskipTests
```

## Docker images

You can build docker images using this commands:

```
./mvnw clean install -Pdocker
```

## Integration tests

Prerequisites:
- Ansible must be installed and available in `$PATH`.
  See [the official documentation](http://docs.ansible.com/ansible/intro_installation.html);
- Java must be available in `$PATH` as `java`.

Integration tests are disabled by default. Use the `it` profile to enable them:

```
./mvnw verify -Pit
```

This will run ITs agains the locally running server and the agent.
To automatically start and stop the server and the agent using docker, use the
`docker` profile:

```
./mvnw verify -Pit -Pdocker
```

# Development

See [the Development](docs/development.md) document.

# Running

See [the Quick Start](docs/quickstart.md) document.

# Examples

See the [examples](docs/examples) directory.

