# Installation

There are several options to install Concord:

- using [Docker Compose](./install/docker-compose.md)
- manually with [Docker](./install/docker.md)
- [Vagrant](./install/vagrant.md)

If you already have access to a Concord deployment or after finishing these
installation steps, you can read the [Introduction to Concord](./index.md)
to understand the basic concepts of Concord or set up your first project with
the [quick start tips](./quickstart.md).

## Database Requirements

Concord requires PostgreSQL 10.4 or higher. The Server's configuration file
provides several important DB connectivity options described below.

### Default Admin API Token

By default, Concord Server automatically generates the default admin API token
and prints it out in the log on the first startup. Alternatively, the token's
value can be specified in [the Server's configuration file](./configuration.md#server-configuration-file):

```
concord-server {
    db {
        changeLogParameters {
            defaultAdminToken = "...any base64 value..."
        }
    }
}
```

### Schema Migration

Concord Server automatically applies DB schema changes every time it starts.
By default, it requires `SUPERUSER` privileges to install additional extensions
and to perform certain migrations.

To deploy the schema using a non-superuser account:

- create a non-superuser account and install required extensions:
    ```sql
    create extension if not exists "uuid-ossp";
    create extension if not exists "pg_trgm";

    create user app with password '...app password...';

    grant all privileges on schema public to app;
    ```
- specify the following options in [the Server's configuration file](./configuration.md#server-configuration-file):

    ```
    concord-server {
        db {
            url = "jdbc:postgresql://host:5432/postgres"
    
            appUsername = "app"
            appPassword = "...app password..."
    
            inventoryUsername = "app"
            inventoryPassword = "...app password..."
    
            changeLogParameters {
                superuserAvailable = "false"
                createExtensionAvailable = "false"
            }
        }
    }
    ```
