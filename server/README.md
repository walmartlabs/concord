# Concord Server

- [db](./db) - main database module, included into [impl](./impl)
to autorollout on start;
- [dist](./dist) - the server's distribution. Includes the main
module, the default cfg file and core plugins;
- [impl](./impl) - main app module;
- [plugins](./plugins) - core server-side plugins;
- [queue-client](./queue-client) - WebSocket based client for
the process queue. Used by the Agent;
- [sdk](./sdk) - set of APIs for server plugins.

## Prometheus metrics authentication

In the default configuration, authentication is not necessary for `/metrics`.
To use authentication, set this value in `server.conf`:

```hocon
concord-server.metrics.auth.enabled = true
```

You can also set `METRICS_AUTH_ENABLED=true` in the server environment.

If you use authentication, the user must have the `readMetrics` permission.
Add a user for Prometheus.
Use `/api/v1/role` to add a role with permissions `["readMetrics"]`.
Give this role to the user.
Use `/api/v1/apikey` or the console to make an API key for the user.
Send the API key in the `Authorization: Bearer <key>` header.

The database migration gives the `readMetrics` permission to `concordAdmin`.
An administrator role is not necessary for the Prometheus user.
