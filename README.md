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

Integration tests are disabled by default. Use the `it` profile to enable them:

```
./mvnw verify -Pit
```

This will run ITs agains the locally running server and the agent.
To automatically start and stop the server and the agent using docker, use the
`docker` profile:

```
./mwn verify -Pit -Pdocker
```

On older systems (e.g. CPUs without `rdrand` or `rdseed`) some ITs can take a long
time to finish due to extensive usage of `SecureRandom` and low entropy available.
As a workaround you can use the `lowEntropy` profile:

```
./mwn verify -Pit -Pdocker -PlowEntropy
```

This profile starts [haveged](https://github.com/harbur/docker-haveged) before running
the ITs.

# Running

See the [the Quick Start](docs/quickstart.md) document.

# Examples

See the [examples](docs/examples) directory.

