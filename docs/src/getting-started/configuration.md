# Configuration

The Concord server can be configured via a configuration file. Typically, this
is done by the administrator responsible for the Concord installation.

A Concord user does not need to be concerned about these settings and instead
needs to define their processes and further details. Check out
[our quickstart guide](./quickstart.md).

The following configuration details are available:

- [Server Configuration File](#server-cfg-file)
- [Server Environment Variables](#server-environment-variables)
- [Agent Configuration File](#agent-cfg-file)
- [Agent Environment Variables](#agent-environment-variables)
- [Common Environment Variables](#common-environment-variables)
- [Default Process Variables](#default-process-variables)
- [GitHub Integration](#github-integration)

<a name="server-cfg-file"/>

## Server Configuration File

Concord Server uses [Typesafe Config](https://github.com/lightbend/config)
format for its configuration files.

The path to the configuration file must be passed via `ollie.conf` JVM
parameter like so:

```bash
java ... -Dollie.conf=/opt/concord/conf/server.conf com.walmartlabs.concord.server.Main
```

When using Docker it can be passed as `CONCORD_CFG_FILE` environment variable.

The complete configuration file for the Server can be found in
[the source code repository](https://github.com/walmartlabs/concord/blob/master/server/dist/src/main/resources/concord-server.conf).

A minimal example suitable for local development (assuming [OpenLDAP](./development.md#oldap)):

```json
concord-server {
    db {
        appPassword = "q1"
        inventoryPassword = "q1"
    }

    secretStore {
        # just some random base64 values
        serverPassword = "cTFxMXExcTE="
        secretStoreSalt = "SCk4KmBlazMi"
        projectSecretSalt = "I34xCmcOCwVv"
    }

    ldap {
        url = "ldap://oldap:389"
        searchBase = "dc=example,dc=org"
        principalSearchFilter = "(cn={0})"
        userSearchFilter = "(cn={0})"
        usernameProperty = "cn"
        userPrincipalNameProperty = ""
        returningAttributes = ["*", "memberOf"]

        systemUsername = "cn=admin,dc=example,dc=org"
        systemPassword = "admin"
    }
}
```

<a name="server-environment-variables"/>

## Server Environment Variables

All parameters are optional.

### API

| Variable       | Description                  | Default value |
|----------------|------------------------------|---------------|
| API_PORT       | API port number to listen on | 8001          |

### Forms

| Variable        | Description                          | Default value               |
|-----------------|--------------------------------------|-----------------------------|
| FORM_SERVER_DIR | Directory to store custom form files | _a new temporary directory_ |

### HTTP(S)

| Variable        | Description                                 | Default value |
|-----------------|---------------------------------------------|---------------|
| SECURE_COOKIES  | Enable `secure` attribute on server cookies | false         |
| SESSION_TIMEOUT | Default timeout for sessions (seconds)      | 1800          |

### Logging

| Variable               | Description                                    | Default value |
|------------------------|------------------------------------------------|---------------|
| ACCESS_LOG_PATH        | Path to the access log, including the filename | _n/a_         |
| ACCESS_LOG_RETAIN_DAYS | How many days to keep access logs              | 7             |

<a name="agent-cfg-file"/>

## Agent Configuration File

Concord Agent uses [Typesafe Config](https://github.com/lightbend/config)
format for its configuration files.

The path to the configuration file must be passed via `ollie.conf` JVM
parameter like so:

```bash
java ... -Dollie.conf=/opt/concord/conf/agent.conf com.walmartlabs.concord.agent.Main
```

When using Docker it can be passed as `CONCORD_CFG_FILE` environment variable.

The complete configuration file for the Agent can be found in
[the source code repository]({{ site.concord_source}}tree/master/agent/src/main/resources/concord-agent.conf).

The configuration file is optional for local development.

## Agent Environment Variables

All parameters are optional.

### Logging

| Variable                  | Description                                                 | Default value |
|---------------------------|-------------------------------------------------------------|---------------|
| DEFAULT_DEPS_CFG          | Path to the default process dependencies configuration file | _empty_       |
| REDIRECT_RUNNER_TO_STDOUT | Redirect process logs to stdout                             | _false_       | 

## Common Environment Variables

### JVM Parameters

| Variable           | Description                           | Default value             |
|--------------------|---------------------------------------|---------------------------|
| CONCORD_JAVA_OPTS  | Additional JVM arguments              | concord-server:           |
|                    |                                       |   `-Xms2g -Xmx2g -server` |
|                    |                                       | concord-agent:            |
|                    |                                       |   `-Xmx256m`              |

### Dependencies

| Variable           | Description                           | Default value |
|--------------------|---------------------------------------|---------------|
| CONCORD_MAVEN_CFG  | Path to a JSON file                   | _empty_       |

See below for the expected format of the configuration file.

Complete example:

```json
{
  "repositories": [
    {
      "id": "central",
      "layout": "default",
      "url": "https://repo.maven.apache.org/maven2/",
      "auth": {
        "username": "...",
        "password": "..."
      },
      "snapshotPolicy": {
        "enabled": true,
        "updatePolicy": "never",
        "checksumPolicy": "ignore"
      },
      "releasePolicy": {
        "enabled": true,
        "updatePolicy": "never",
        "checksumPolicy": "ignore"
      }
    },

    {
      "id": "private",
      "url": "https://repo.example.com/maven2/"
    } 
  ]
}
```

Parameters:
- `id` - string, mandatory. Arbitrary ID of the repository;
- `layout` - string, optional. Maven repository layout. Default value is
`default`;
- `url` - string, mandatory. URL of the repository;
- `auth` - object, optional. Authentication parameters, see
  the [AuthenticationContext](https://maven.apache.org/resolver/apidocs/org/eclipse/aether/repository/AuthenticationContext.html)
  javadoc for the list of accepted parameters. Common parameters:
  - `username`, `password` - credentials;
  - `preemptiveAuth` - if `true` Concord performs pre-emptive authentication. 
  Required if the remote server expects credentials in all requests (e.g. S3 buckets).
- `snapshotPolicy` and `releasePolicy` - object, optional. Policies for
snapshots and release versions. Parameters: 
  - `enabled` - boolean, optional. Enabled or disables the category. Default
  value is `true`;
  - `updatePolicy` - string, optional. See [RepositoryPolicy](https://maven.apache.org/resolver/apidocs/org/eclipse/aether/repository/RepositoryPolicy.html)
  javadoc for the list of accepted values;
  - `checksumPolicy` - string, optional. See [RepositoryPolicy](https://maven.apache.org/resolver/apidocs/org/eclipse/aether/repository/RepositoryPolicy.html)
  javadoc for the list of accepted values;

## Default Process Variables

As a Concord administrator, you can set default variable values that
are automatically set in all process executions.

This, for example, allows you to set global parameters such as the connection
details for an SMTP server used by the [SMTP task]({{ site.concord_plugins_v2_docs }}/smtp.md) in one
central location separate from the individual projects.

The values are configured in a YAML file. The path to the file and the name are
configured in [the server's configuration file](#server-cfg-file). The
following example, shows how to configure an SMTP server to be used by all
processes. As a result, project authors do not need to specify the SMTP server
configuration in their
own `concord.yml`.

```yml
configuration:
  arguments:
    smtpParams:
      host: "smtp.example.com"
      port: 25

    # another example
    slackCfg:
      authToken: "..."
```

## GitHub Integration

### Repository Access

To access external Git repositories Concord supports both the username and
password, and the SSH key pair authentication.

Additionally, an access token can be configured to use when no custom
authentication specified:

```
# concord-server.conf
concord-server {
    git {
        # GitHub username and an access token separated by a colon
        oauth: "jsmith:af3f...f"
    }
}
```

The same token must be added to the Agent's configuration as well:

```
# concord-agent.conf
concord-agent {
    git {
        oauth: "..."
    }
}
```

### Webhooks

Concord supports both repository and organization level hooks.

Here's a step-by-step instruction on how to configure Concord to use GitHub
webhooks:

Configure the shared secret:

```
# concord-server.conf
concord-server {
    github {
        githubDomain = "github.com"
        secret = "myGitHubSecret"
    }
}
```

Create a new webhook on the GitHub repository or organizations settings page:

<img src="../../assets/img/screenshots/gh-webhook-page.png" alt="github webhook configuration" class="img-responsive"/>

Use `Content-Type: application/json` and a secret you specified in the
`concord-server.conf` file.

**Note:** the `useInitiator` [feature](../triggers/github.md) requires
a Concord environment with an AD/LDAP server. If you wish to use Concord without
an AD or LDAP server, or your GitHub users are not the same as your AD/LDAP users,
use `useInitiator: false` or omit it (`false` is the default value). In this case
all processes triggered by GitHub would have the built-in `github` user as their
initiator.
