# OpenID Connect Integration

This plugin adds support for authentication with OpenID Connect.

## Usage

Add the required parameters to the server configuration file. The following example uses [Okta](https://www.okta.com/):

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

For development on `localhost`, use this callback URL:

```
http://localhost:8001/api/service/oidc/callback?client_name=oidc
```

The plugin requires the `client_name=oidc` query parameter.
Include this parameter in the provider configuration.
The plugin uses these scopes: `openid`, `profile`, `email`, and `groups`.
The provider might not enable all these scopes by default.
For example, Okta does not provide the `groups` scope by default.
To add this scope, go to "Security" -> "API" -> "Authorization Servers" -> your_server -> "Scope".

### Interactive Login

Configure the Concord Console to use custom login and logout URLs:
- create a `cfg.js` (use [the default file](../../../console2/public/cfg.js) as an example):
  ```javascript
  window.concord = {
      loginUrl: '/api/service/oidc/auth',
      logoutUrl: '/api/service/oidc/logout'
  };
  ```
- mount the file in the server container:
  ```
  docker run ... -v /path/to/cfg.js:/opt/concord/console/cfg.js:ro walmartlabs/concord-server
  ```

When you access the Concord Console, the browser redirects you to the OpenID Connect provider's sign-in page.

### API Tokens

- Get an access token from the identity provider. For Okta, you can use [the implicit flow](https://developer.okta.com/docs/guides/implement-implicit/use-flow/).
  The token must allow the `openid`, `email`, and `profile` scopes.
- Call a Concord API endpoint with the token:
  ```
  curl -ik -H 'Authorization: Bearer TOKEN' http://localhost:8001/api/service/console/whoami
  ```
