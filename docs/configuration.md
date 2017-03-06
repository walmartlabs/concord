# Configuration

Most of the configuration done via environment variables (e.g. passed with a `docker run` command).

## Server

### Environment variables

All parameters are optional.

**Agent**

| Variable               | Description                                                     | Default value           |
|------------------------|-----------------------------------------------------------------|-------------------------|
| AGENT_URL              | URL of an agent instance                                        | `http://localhost:8002` |

**Database**

| Variable    | Description                                                     | Default value                        |
|-------------|-----------------------------------------------------------------|--------------------------------------|
| DB_DIALECT  | Type of the used database. Supported dialects: `H2`, `POSTGRES` | `H2`                                 |
| DB_DRIVER   | FQN of the driver's class.                                      | `org.h2.Driver`                      |
| DB_URL      | JDBC URL of the database.                                       | `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1` |
| DB_USERNAME | Username to connect to the database.                            | `sa`                                 |
| DB_PASSWORD | Password to connect to the database.                            | _empty_                              |

**Log file store**

| Variable      | Description                                                 | Default value               |
|---------------|-------------------------------------------------------------|-----------------------------|
| LOG_STORE_DIR | Path to a directory where agent's log files will be stored. | _a new temporary directory_ |

**Secret store**

| Variable          | Description                                                                       | Default value |
|-------------------|-----------------------------------------------------------------------------------|---------------|
| SECRET_STORE_SALT | Store's salt value. If changed, all previously created keys will be inaccessable. |               |

**Templates**

| Variable           | Description                                                      | Default value          |
|--------------------|------------------------------------------------------------------|------------------------|
| DEPS_STORE_DIR     | Local Maven repository, used to resolve template's dependencies. | `$HOME/.m2/repository` |

## Agent

### Environment variables

All parameters are optional.

| Variable          | Description                                     | Default value               |
|-------------------|-------------------------------------------------|-----------------------------|
| AGENT_LOG_DIR     | Directory to store payload execution log files. | _a new temporary directory_ |
| AGENT_PAYLOAD_DIR | Directory to store unpacked payload files.      | _a new temporart directory_ |
| AGENT_JAVA_CMD    | Path to `java` executable.                      | `java`                      |