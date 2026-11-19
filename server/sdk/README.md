# Server SDK

The `concord-server-sdk` artifact contains contracts for server-side plugins.
See [server plugins](../plugins/README.md) for deployment.

## External identity contracts

The `com.walmartlabs.concord.server.sdk.identity` package separates event actor
verification from authoritative profile and group lookup:

- `ExternalEventIdentityResolver` verifies the actor and returns `Unhandled`,
  `Resolved` or `Rejected`.
- `ExternalIdentityProvider` retrieves an authoritative lifecycle state, optional
  profile fields and a complete membership set for an `ExternalIdentityRef`.

These are foundation contracts only. This module does not register or invoke
providers, provision external users, persist memberships or change authorization.
The server implementation now has persistence and explicit refresh primitives
described below. Event integration and execution support remain disabled.

### Persistence foundation

The server's `ExternalIdentityDao` provisions a disabled `EXTERNAL` user and a
unique `(providerId, authority, subjectId)` binding in one transaction. Each user
has at most one binding. Concord generates an opaque username rather than using
a mutable external login. Repeated provisioning preserves the original user's
profile and disable state and does not add default teams, roles or memberships.

Persisted references support provider IDs up to 128 characters and authority and
subject IDs up to 512 characters each. Identity components remain case-sensitive.

Authoritative aliases reuse `EXTERNAL_APP_USERS`. Claiming an alias is idempotent
for the same user, but conflicts with any other user's mapping. The legacy mapping
writer retains its existing reassignment behavior for nonauthoritative mappings
and cannot overwrite an authoritative alias. Deleting a user cascades to its
binding and aliases.

This is not an execution feature: no external `UserInfoProvider` is registered,
ordinary user provisioning rejects `EXTERNAL`, and internal impersonation and
API-key authentication explicitly reject these users even if enabled manually.
Do not remove these gates until lifecycle, freshness and restricted-delegation
enforcement are integrated.

Upgrade all server nodes before provisioning external identities or claiming
authoritative aliases. Older nodes do not recognize `EXTERNAL` and their legacy
mapping writer does not enforce authoritative-alias protection.

### Explicit membership synchronization

Plugins register `ExternalIdentityProvider` using Guice's
`Multibinder.newSetBinder(binder, ExternalIdentityProvider.class)`. Provider IDs
must be unique and contain between 1 and 128 characters; invalid registrations
fail server initialization.

The server's `ExternalIdentitySynchronizer.refresh(identity)` refreshes an
already provisioned identity. This slice does not introduce a REST endpoint,
automatic provisioning on events, a background scheduler or a freshness TTL.
Those callers and policies will be integrated separately before execution can
be enabled.

Each refresh reserves a database ordering token before contacting the provider.
The remote lookup does not hold a database lock. Only the newest attempt may
commit, and a token is consumed at most once. Tokens also bind the Concord user
ID so an old lookup cannot apply to a deleted and subsequently recreated identity.
A newer failed attempt still supersedes older in-flight attempts.

Successful refresh atomically replaces the complete cached group set, updates
the optional display name and email (including clearing absent values), and
stores `SOURCE_STATUS` and `LAST_SUCCESSFUL_SYNC`. Provider failures, null or
mismatched snapshots and superseded results are explicit errors; they do not
clear memberships or advance successful-sync metadata. Successful updates are
audited. A snapshot rejected by the database rolls back all its changes.

`ACTIVE` with zero groups removes all provider-derived memberships without
claiming the identity is missing. `INACTIVE` and `NOT_FOUND` clear those
memberships and record the authoritative source state. Refresh never enables or
disables the Concord user, overrides an administrator's disable decision, or
changes direct team/role grants. Source lifecycle and administrator disable
state remain separate and must both be checked by future execution integration.

External memberships join existing team mappings, but the role view explicitly
excludes them from group-derived global roles. Explicit direct roles retain their
existing semantics. LDAP-group form conditions reject external identities, and
the LDAP group writer rejects attempts to modify their memberships. Existing
LDAP, LOCAL and SSO group behavior is preserved.

Persisted membership is a cache, not sufficient authorization. A failed refresh
may leave the last successful membership set in storage; no external execution
is enabled by this slice, and future use must enforce freshness, source state,
administrator disable state and restricted delegation before using that cache.

### Identity and trust

An identity reference consists of a stable provider ID, a canonical authority
identifier and an immutable subject ID. Components are case-sensitive and are
preserved verbatim. Use a configured authority, not a host taken from an event
body; display names and logins must not serve as immutable subject identifiers.

The server creates `ExternalEventContext` after authenticating the delivery.
Its JSON body is an immutable string so multiple resolvers cannot mutate shared
event data. Plugins may parse it with their existing JSON tooling. Do not log the
body indiscriminately: it may contain sensitive information.

`SIGNED_PAYLOAD` means a trusted signature covered the body.
`ENDPOINT_CREDENTIAL` authenticates endpoint access, not payload actor claims;
the resolver must independently verify those claims before returning `Resolved`.
A recipient installation or resource owner is not necessarily the event actor.

Return `Unhandled` only for actors outside the resolver's responsibility.
Recognized but unacceptable identities return `Rejected`; lookup or verification
failures throw `ExternalIdentityException`. Neither outcome permits fallback to a
different execution identity. Multiple resolvers claiming the actor must fail
rather than depend on registration order.

### Membership and lifecycle

A provider's `getId()` must be stable and nonblank. Duplicate provider IDs must
be rejected by server integration. A fetched snapshot must identify exactly the
requested reference, and its membership set must be complete, including all
pages if the authority paginates results.

| Result | Meaning |
|---|---|
| `ACTIVE`, nonempty groups | Complete current memberships. |
| `ACTIVE`, empty groups | Existing active identity with no memberships. |
| `INACTIVE`, empty groups | Authoritatively deactivated identity. |
| `NOT_FOUND`, empty groups | Authority definitively reports no such identity. |
| `ExternalIdentityException` | No authoritative result; never convert to empty groups or `NOT_FOUND`. |

Group identifiers must match the canonical strings in Concord team mappings.
External memberships are intended for team-derived access, not group-derived
global roles or LDAP-specific form access.

Concord server integration owns atomic membership replacement, alias uniqueness,
freshness timestamps, refresh ordering, administrator-disable preservation and
ordinary resource authorization. Providers do not receive database handles or
permission to modify arbitrary Concord users. A resolved actor is not itself
permission to execute, and a cached alias must not bypass lifecycle checks.

### Planned integration slices

Each slice is intended to be a separate branch based on its predecessor:

1. SDK contracts (this module; no runtime behavior change).
2. External users, authoritative identity bindings and conflict-safe aliases.
3. Membership synchronization and team-only RBAC semantics.
4. GitHub actor-resolution integration, preserving human mappings and
   `useInitiator` behavior.
5. Lifecycle, freshness and restricted-delegation enforcement for new and restored
   execution contexts. A shared app's accumulated team grants must not become
   unrestricted authority for tenant-controlled workflow code.
6. API and console representation without ordinary manual external provisioning.

Intermediate slices must not enable execution as an external identity before
the required lifecycle and authorization enforcement is in place.
