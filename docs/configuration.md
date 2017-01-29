# Configuration

Most of the configuration done via environment variables (e.g. passed with a `docker run` command).

## Server

### Environment variables

All parameters are optional.

**General**

| Variable               | Description                                                     | Default value           |
|------------------------|-----------------------------------------------------------------|-------------------------|
| SERVER_PORT            | Listening port of the API endpoint.                             | `8001`                  |
| SERVER_EXPOSED_ADDRESS | Address of the API endpoint, which will be used by the agents.  | `http://127.0.0.1:8001` |
| AGENT_URL              | URL of an agent instance                                        | `http://localhost:8002` |

**Database**

| Variable    | Description                                                     | Default value                        |
|-------------|-----------------------------------------------------------------|--------------------------------------|
| DB_DIALECT  | Type of the used database. Supported dialects: `H2`, `POSTGRES` | `H2`                                 |
| DB_DRIVER   | FQN of the driver's class.                                      | `org.h2.Driver`                      |
| DB_URL      | JDBC URL of the database.                                       | `jdbc:h2:mem:test;DB_CLOSE_DELAY=-1` |
| DB_USERNAME | Username to connect to the database.                            | `sa`                                 |
| DB_PASSWORD | Password to connect to the database.                            | _empty_                              |

**Log file storage**

| Variable      | Description                                                 | Default value               |
|---------------|-------------------------------------------------------------|-----------------------------|
| LOG_STORE_DIR | Path to a directory where agent's log files will be stored. | _a new temporary directory_ |
