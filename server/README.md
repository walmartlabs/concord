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

The SDK's `security` package defines independent identity-directory,
provisioning, and authorization-provider contracts. Provider IDs are stable,
open strings; external identity authorities and subjects are case-sensitive.
