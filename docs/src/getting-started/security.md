# Security

- [Authentication](#authentication)
- [Secret Management](#secret-management)

## Authentication

Concord supports multiple authentication methods:
- Concord [API tokens](#using-api-tokens);
- basic authentication (username/password);
- temporary [session tokens](#using-session-tokens);
- OpenID Connect, via [the OIDC plugin](https://github.com/walmartlabs/concord/tree/master/server/plugins/oidc).

Plugins can implement additional authentication methods.

### Using API Tokens

The key must be passed in the `Authorization` header on every API request. For
example:

```
curl -v -H "Authorization: <value>" ...
```

API keys are managed using the [API key](../api/apikey.md) endpoint or using
the UI.

### Using Username and Password

For example:
```
curl -v -u myuser:mypwd ...
```

The actual user record will be created on the first successful authentication
attempt. After that, it can be managed as usual, by using
the [User](../api/user.md) API endpoint.

Username/password authentication uses an LDAP/Active Directory realm. Check
[Configuration](./configuration.md#server-configuration-file) document for details.

### Using Session Tokens

For each process Concord generates a temporary "session token" that can be used
to call Concord API. The token is valid until the process reaches one of
the final statuses:
- `FINISHED`
- `FAILED`
- `CANCELLED`
- `TIMED_OUT`.

The session token must be passed in the `X-Concord-SessionToken` header:

```
curl -v -H "X-Concord-SessionToken: <value>" ...
```

Such API requests use the process's security principal, i.e. they run on behalf
of the process' current user.

The current session token is available as `${processInfo.sessionToken}`
[variable](../processes-v1/index.md#provided-variables).

## Secret Management

Concord provides an API to create and manage various types of secrets that can
be used in user flows and for Git repository authentication.

Secrets can be created and managed using
[the Secret API endpoint](../api/secret.md) or the UI.

Supported types:
- plain strings and binary data (files) ([example](../api/secret.md#example-single-value-secret);
- username/password pairs ([example](../api/secret.md#example-username-password-secret));
- SSH key pairs ([example](../api/secret.md#example-new-key-pair)).

Secrets can optionally be protected by a password provided by the user.
Non password-protected secrets are encrypted with an environment specific key
defined in Concord Server's configuration.

Additionally, Concord supports "encrypted strings" - secrets that are stored
"inline", directly in Concord YAML files:

```yaml
flows:
  default:
    - log: "Hello, ${crypto.decryptString('aXQncyBub3QgYW4gYWN0dWFsIGVuY3J5cHRlZCBzdHJpbmc=')}"
``` 

Concord encrypts and decrypts such values by using a project-specific
encryption key. In order to use encrypted strings, the process must run in a project.

The [crypto]({{ site.concord_plugins_v2_docs }}/crypto.md) task can be used to work with secrets and
encrypted strings.
