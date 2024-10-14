# OpenID Connect Integration

Adds support for authentication using OpenID Connect.

## Usage

Add the necessary parameters to the Server's configuration file. Example for [Okta](https://www.okta.com/):

```
concord-server {
    oidc {
        enabled = true
        clientId = "********************"
        secret = "****************************************"
        discoveryUri = "https://myorg.okta.com/oauth2/default/.well-known/openid-configuration"
        urlBase = "http://localhost:8001"
        afterLoginUrl = "http://localhost:8001"
        afterLogoutUrl = "http://localhost:8001/#/logout/done"
        roles = [
            "concordAdmin"
        ]
    }
}
```

For running in development mode (i.e. on `localhost`), callback URLs must be
in the form of

```
http://localhost:8001/api/service/oidc/callback?client_name=oidc
```

Note the `client_name=oidc` query parameter, it is required by the plugin and
must be present in the provider's configuration.

The plugin uses the following scopes: `openid`, `profile`, `email`, `groups`.
Which may or may not be enabled by default in the provider's configuration.

Okta, for example, does not provide the `groups` scope by default. You can
add it in the "Security" -> "API" -> "Authorization Servers" -> your_server ->
"Scope" section.

### Interactive Login

Configure the Concord Console to use custom logout/login URLs:
- create a `cfg.js` (use [the default file](../../../console2/public/cfg.js) as an example):
  ```javascript
  window.concord = {
      loginUrl: '/api/service/oidc/auth',
      logoutUrl: '/api/service/oidc/logout'
  };
  ```
- mount in into the Server's container:
  ```
  docker run ... -v /path/to/cfg.js:/opt/concord/console/cfg.js:ro walmartlabs/concord-server
  ```

When accessing the Concord Console you should be redirected to the OpenID Connect
provider's sign-in page.

### API Tokens

- get an access token from the ID provider. For Okta, you can use
[the implicit flow](https://developer.okta.com/docs/guides/implement-implicit/use-flow/).
The token must allow `openid`, `email` and `profile` scopes;
- call a Concord API endpoint using the token:
  ```
  curl -ik -H 'Authorization: Bearer TOKEN' http://localhost:8001/api/service/console/whoami
  ```
