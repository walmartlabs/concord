# Temporary OpenAPI migration check

`OpenApiContractTest` protects the RESTEasy/Jakarta migration and is intended to
be removed after the migration. It is not a permanent API evolution policy.

The baseline is the YAML packaged in the published **2.46.0** JARs:

| Case | Baseline artifact | Current specification producer |
| --- | --- | --- |
| `server` | `com.walmartlabs.concord.server:concord-server-impl` | `server/impl` |
| `ansible` | `com.walmartlabs.concord.server.plugins.ansible:concord-ansible-plugin` | `server/plugins/ansible/impl` |
| `noderoster` | `com.walmartlabs.concord.server.plugins.noderoster:concord-noderoster-plugin` | `server/plugins/noderoster/impl` |

Maven unpacks only the Swagger YAML resources into
`client2/target/openapi-migration-baseline` during `generate-test-resources`.
The version is pinned by `openapi.migration.baseline.version` in `client2/pom.xml`;
it does not follow the current project version or a changing SNAPSHOT. The first
run needs repository access; subsequent runs can use cached release artifacts.

The current inputs are the same generated files used by all three client generators.
Each producer generates its YAML during `compile`. The existing dependency on
`server/dist` orders all producers before `client2` in the normal reactor build.
A missing current input fails instead of falling back to an installed JAR.

Comparison covers the complete documents, including paths, schemas and validation
constraints. Object field order and YAML formatting are ignored; array order and
values are preserved. The test does not start a server or database.

One explicit baseline adjustment accounts for `LogSegmentUpdateRequest.statusUpdatedAt`
(`string`, `date-time`), added after 2.46.0 in commit `f11201f10` before the migration.
The test adds that exact schema to the baseline; it does not ignore the field or
permit arbitrary additions.

## Running and investigating failures

With dependencies already built locally:

```sh
./mvnw -pl server/impl,server/plugins/ansible/impl,server/plugins/noderoster/impl,client2 test \
  -Dtest=OpenApiContractTest -Dsurefire.failIfNoSpecifiedTests=false
```

For a fresh checkout, use the normal repository build prerequisites (including
Docker for database modules) and add `-am` to build dependencies in the reactor.

Each case writes `actual.yaml` and `diff.txt` to
`client2/target/openapi-contract/<api>/`. Diff locations are JSON Pointers.
Review differences against the extracted release YAML. A difference can also
reflect an API change since 2.46.0; investigate it separately from the migration.
Do not update the baseline merely to make a migration regression pass.

## Removal

After the migration is verified, remove `OpenApiContractTest`, this document,
the README reference, the baseline property and dependency-plugin execution,
and the test-only YAML/JUnit parameter dependencies if no other tests need them.
No committed YAML snapshots need maintenance.
