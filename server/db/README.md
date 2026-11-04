# Main database code generation

Normal `server/db` builds restore the committed schema-only baseline in
`src/codegen/schema.sql` using JDBC, then run jOOQ. They do not run Liquibase
or rewrite the baseline. Runtime migrations are unchanged; the baseline and
its build-only helper are not packaged in the main JAR.

After changing migrations, custom-change implementations, codegen migration
configuration, or the baseline helper, regenerate from the repository root:

```shell
./mvnw -pl server/db -am -Prefresh-codegen-baseline -DskipTests install
```

Commit both generated files, `server/db/src/codegen/schema.sql` and
`server/db/src/codegen/schema.properties`. Do not edit them by hand. Refresh
builds current reactor prerequisites, runs canonical migrations, and verifies
a native dump/JDBC restore round trip before publishing. Missing or stale
baselines fail in `initialize`, even with `-DskipTests`.

To compare the committed baseline independently against canonical migrations
without changing it, after installing current prerequisites:

```shell
./mvnw -pl server/db -Pverify-codegen-baseline -DskipTests package
```

Both maintenance profiles require the module-managed PostgreSQL container and
Docker CLI pointed at the same daemon as Maven. They cannot be combined with
each other, `-Plooper`, or migration skip flags. `package` reaches the existing
container-stop phase. No host PostgreSQL client is required.

Normal `-Plooper` builds use the externally supplied host/port without invoking
Docker CLI. Supply a fresh, exclusively assigned disposable database: `public`
must exist and have no relations, functions, or types. Nonempty databases are
rejected without resetting them. The actual PostgreSQL version must match the
manifest; changing only the image registry or connection credentials does not
invalidate the baseline.
