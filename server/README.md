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

Account references accept an explicit identity key `{providerId, authority,
subject}` or a user UUID. Resolve identities with `GET /api/v2/user/identity`;
update direct roles with `PUT /api/v2/user/{id}/roles`. Policy links accept
`userId` or `identity`; list and unlink user links at
`GET /api/v2/policy/byUser/{userId}` and
`DELETE /api/v2/policy/{policyName}/users/{userId}`. Team members can be removed
by UUID with `DELETE /api/v2/org/{orgName}/team/{teamName}/users`.
Explicit identity selectors cannot be combined with UUID or legacy selector
fields. Set `security.legacy.enabled=false` to reject legacy selectors.
Team memberships expose `membershipKind` (`DIRECT` or `SOURCE`) and
`grantSources` entries identifying the contributing source, group, and role;
group is null for a source's direct team contribution.

Authorization group mappings are qualified by source. LDAP refreshes replace
only LDAP-owned observations; direct role and team grants remain independent.
`security.authorization.sources.<id>.required` controls whether an unavailable
or missing source prevents access, and `.maxAge` controls its observation lease.

Protected requests and internal run-as scopes prepare authorization against
the current account and source observations. An unavailable required source
denies access rather than reusing its last successful grants.

Before starting background tasks or accepting HTTP requests, the server converts
persisted initial and current execution identities to versioned JSON. Conversion
uses an advisory lock and commits each row independently, preserving encryption
and file mode.

Stop all old server nodes before upgrading and retain a database backup for
rollback; mixed old and new servers sharing a database are not supported. Keep
all previously used authenticator plugin JARs installed for the first upgraded
startup so legacy serialized principals can be read. Once startup conversion
completes, an authenticator may be removed and the server restarted. Old HTTP
sessions are not migrated; basic/API-key remember-me uses its existing separate
serializer.

If conversion fails, startup aborts before background tasks and HTTP begin. The
error identifies the failing state row; restore the missing plugin class or JAR
when applicable, or repair the corrupt state, then restart. The failing row is
left unchanged, and previously converted rows remain valid and are skipped on
retry.
