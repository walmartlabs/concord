# Server OpenAPI contract

`server-api.yaml` is a snapshot of the complete published server OpenAPI document.
`OpenApiContractTest` compares it with the resource produced by the existing
`swagger-maven-plugin` execution during `compile`, which is packaged in the server.
It does not run a separate scanner or start the server or a database.

The comparison covers paths, methods, parameters, request/response schemas,
validation constraints and the remaining document metadata. Object field order
and YAML formatting are ignored. Array order is preserved, including examples
and defaults; changes to array order therefore require review too.

## Running

From the repository root, with the module's dependencies built locally:

```sh
./mvnw -pl server/impl test -Dtest=OpenApiContractTest
```

The test also runs as part of the normal Maven test lifecycle and CI.
For a fresh checkout, use the normal repository build prerequisites (including
Docker for the database module) and add `-am -Dsurefire.failIfNoSpecifiedTests=false`
to build dependencies in the same reactor.

## Reviewing an intentional API change

1. Run the test. It writes the generated document to
   `server/impl/target/openapi-contract/actual.yaml` and a field-level diff to
   `server/impl/target/openapi-contract/diff.txt`. Diff locations are JSON Pointers;
   `/` within a path name is escaped as `~1`.
2. Review each difference against the intended API change. Missing constraints
   after a dependency upgrade are regressions, not a reason to refresh the baseline.
3. After reviewing an intentional change, update the snapshot:

   ```sh
   cp server/impl/target/openapi-contract/actual.yaml server/impl/src/test/resources/openapi/server-api.yaml
   ```

4. Review the snapshot diff, rerun the test and commit the snapshot with the API change.

The test never updates this baseline automatically.
