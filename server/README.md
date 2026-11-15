# Concord Server

- [db](./db) contains the primary database module. The [impl](./impl) module includes it to run automatic rollouts at startup.
- [dist](./dist) contains the server distribution, the primary module, the default configuration file, and core plugins.
- [impl](./impl) contains the primary application module.
- [plugins](./plugins) contains core server-side plugins.
- [queue-client](./queue-client) contains a WebSocket client for the process queue. The Agent uses this client.
- [sdk](./sdk) provides APIs for server plugins.
