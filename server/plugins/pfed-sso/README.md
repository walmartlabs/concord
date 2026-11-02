# PingFederate SSO plugin

This plugin adds support for authentication with [PingFederate](https://www.pingidentity.com/en/software/pingfederate.html).

## Usage

Include this module in the server's [dist](../../dist) module.

Add the required parameters to the server configuration file. For example:

```
concord-server {
    sso {        
        pfed {
             enabled = true
        }
        authEndpointUrl = "https://pfed.example.com/as/authorization.oauth2"
        tokenEndpointUrl = "https://pfed.example.com/as/token.oauth2"
        logoutEndpointUrl = "https://pfed.example.com/as/revoke_token.oauth2"
        redirectUrl = "https://concord.example.com/api/service/sso/redirect"
        userInfoEndpointUrl = "https://pfed.example.com/idp/userinfo.openid"
        tokenSigningKeyUrl = "https://pfed.example.com/pf/JWKS"
        clientId = "**************************"
        clientSecret = "*****************************"
        tokenServiceReadTimeout = 10000
        tokenServiceConnectTimeout = 10000
        validateNonce = false
        tokenSignatureValidation = true # Option to enable or disable token signature validation
        tokenSigningKey = null  # providing direct key here overrides the remote signing key
        autoCreateUsers = false
    }
}
```

See the [PingFederate developer guide](https://docs.pingidentity.com/bundle/developer/page/erq1601508087286.html#developer-OpenIDConnect10DeveloperGuide-7) for instructions to implement OpenID Connect with PingFederate.

### Interactive Login

Configure the Concord Console to use custom login and logout URLs:

- create a `cfg.js` (use [the default file](../../../console2/public/cfg.js) as an example):
  ```javascript
  window.concord = {
      loginUrl: '/api/service/sso/auth',
      logoutUrl: '/api/service/sso/logout'
  };
  ```
- mount the file in the server container:
  ```
  docker run ... -v /path/to/cfg.js:/opt/concord/console/cfg.js:ro walmartlabs/concord-server
  ```

When you access the Concord Console, the browser redirects you to the PingFederate sign-in page.
