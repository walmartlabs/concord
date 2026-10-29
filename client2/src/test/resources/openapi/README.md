# OpenAPI contracts used by the generated clients

`OpenApiContractTest` compares all three complete published OpenAPI documents
against their snapshots. Its input files are the same `inputSpec` files consumed
by the corresponding `openapi-generator-maven-plugin` executions:

| Case / snapshot | Specification producer | Generated client |
| --- | --- | --- |
| `server` / `server-api.yaml` | `server/impl` | `client2` |
| `ansible` / `ansible-api.yaml` | `server/plugins/ansible/impl` | `server/plugins/ansible/client2` |
| `noderoster` / `noderoster-api.yaml` | `server/plugins/noderoster/impl` | `server/plugins/noderoster/client2` |

Each producer generates its YAML during `compile`. The test lives in `client2`,
whose existing dependency on `server/dist` puts all three producers earlier in
the normal reactor build. It reads their generated files directly, so a missing
file fails even if an older server/plugin JAR is available in the local repository.
It does not run a separate scanner or start a server or database.

The comparison covers paths, methods, parameters, request/response schemas,
validation constraints and the remaining document metadata. Object field order
and YAML formatting are ignored. Array order is preserved, including examples
and defaults; changes to array order therefore require review too.

## Running

From the repository root, with dependencies already built locally, generate all
three specifications and run the three test cases:

```sh
./mvnw -pl server/impl,server/plugins/ansible/impl,server/plugins/noderoster/impl,client2 test \
  -Dtest=OpenApiContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

The test also runs as part of the normal Maven test lifecycle and CI.
For a fresh checkout, use the normal repository build prerequisites (including
Docker for the database modules) and add `-am` to build dependencies in the reactor.
The generators and compilation in the three client modules separately check that
Java clients can still be built; this test protects their input contracts.

## Reviewing an intentional API change

1. Run the test. For each API it writes the generated document to
   `client2/target/openapi-contract/<api>/actual.yaml` and a field-level diff to
   `client2/target/openapi-contract/<api>/diff.txt`. Diff locations are JSON Pointers;
   `/` within a path name is escaped as `~1`.
2. Review each difference against the intended API change. Missing constraints
   after a dependency upgrade are regressions, not a reason to refresh a baseline.
3. After reviewing an intentional change, update only its snapshot. For example:

   ```sh
   cp client2/target/openapi-contract/ansible/actual.yaml client2/src/test/resources/openapi/ansible-api.yaml
   ```

4. Review the snapshot diff, rerun the test and commit the snapshot with the API change.

The test never updates its baselines automatically. A new API requires another
parameter row with its generator's input file and a corresponding snapshot.
