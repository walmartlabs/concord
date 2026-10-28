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
        profileProviderId = "local"
        authorizationSources = ["oidc"]
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


OIDC accounts are linked by the discovery document's issuer and the verified
userinfo `sub`, not by email on each login. Existing LOCAL accounts are eligible
for a one-time adoption using the historical lowercased-email rule; changing
email later does not relink an account.

`oidc.profileProviderId` defaults to `local`, and `oidc.authorizationSources`
defaults to `["oidc"]`. Role and team mappings contribute only OIDC-owned
grants; direct roles and team memberships are independent. Userinfo attributes
are retained without tokens for at most one hour, capped by the token response's
expiry when available. Reusing a cached profile does not renew that deadline.
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
