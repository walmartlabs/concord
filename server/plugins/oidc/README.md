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
        discoveryUri = "https://myorg.okta.com/.well-known/openid-configuration"
        urlBase = "http://localhost:8001"
        afterLoginUrl = "http://localhost:8001"
        afterLogoutUrl = "http://localhost:8001/#/logout/done"
    }
}
```
