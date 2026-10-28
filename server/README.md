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

The security migration snapshots existing legacy accounts and backfills local
and canonical LDAP identities without changing account UUIDs. Canonical LDAP
collisions abort the backfill; resolve the conflicting accounts before retrying.

User identity links can be inspected at `GET /api/v2/user/{id}/identities`;
administrators can link or retire identities and select an account's profile
provider through the corresponding v2 user endpoints. Retired identity keys
remain reserved and require explicit administrative recovery.
