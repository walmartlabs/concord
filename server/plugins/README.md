# Server-side Plugins

To use those plugins they must be included into the Server's classpath. See the [dist](../dist) module. 

The default server distribution includes the `ldap` plugin. It supplies the
LDAP directory and authorization source independently of password authentication.
Set `ldap.authenticationEnabled=false` to disable LDAP password login while
retaining directory lookup and authorization. Without a configured LDAP URL,
the authorization source is inactive. Custom distributions can omit the plugin;
policies that reference its capabilities must then be reconfigured.