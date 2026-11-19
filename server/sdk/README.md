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
described below. GitHub event resolution is integrated; external execution
remains disabled.

### Persistence foundation

The server's `ExternalIdentityDao` provisions a disabled `EXTERNAL` user and a
unique `(providerId, authority, subjectId)` binding in one transaction. Each user
has at most one binding. Concord generates an opaque username rather than using
a mutable external login. Repeated provisioning preserves the original user's
profile and disable state and does not add default teams, roles or memberships.

Persisted references support provider IDs up to 128 characters and authority and
subject IDs up to 512 characters each. Identity components remain case-sensitive.

Authoritative aliases reuse `EXTERNAL_APP_USERS`. Claiming an alias is idempotent
for the same user, but conflicts with any other user's mapping. The user mapping
writer retains its existing reassignment behavior for nonauthoritative mappings
and cannot overwrite an authoritative alias. Deleting a user cascades to its
binding and aliases.

This is not an execution feature: no external `UserInfoProvider` is registered,
ordinary user provisioning rejects `EXTERNAL`, and internal impersonation and
API-key authentication explicitly reject these users even if enabled manually.
Do not remove these gates until lifecycle, freshness and restricted-delegation
enforcement are integrated.

Upgrade all server nodes before provisioning external identities or claiming
authoritative aliases. Older nodes do not recognize `EXTERNAL` and their
mapping writer does not enforce authoritative-alias protection.

### Explicit membership synchronization

Plugins register `ExternalIdentityProvider` using Guice's
`Multibinder.newSetBinder(binder, ExternalIdentityProvider.class)`. Provider IDs
must be unique and contain between 1 and 128 characters; invalid registrations
fail server initialization.

The server's `ExternalIdentitySynchronizer.refresh(identity)` explicitly refreshes
an already provisioned identity. The lifecycle manager described below also uses
this primitive for on-demand refresh. There is no REST refresh endpoint,
automatic provisioning on events or background scheduler.

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
state remain separate and are both checked by lifecycle verification.

External memberships join existing team mappings, but the role view explicitly
excludes them from group-derived global roles. Explicit direct roles retain their
existing semantics. LDAP-group form conditions reject external identities, and
the LDAP group writer rejects attempts to modify their memberships. Existing
LDAP, LOCAL and SSO group behavior is preserved.

Persisted membership is a cache, not sufficient authorization. A failed refresh
may leave the last successful membership set in storage; no external execution
is enabled by this slice. Lifecycle verification enforces freshness, source state
and administrator disable state; execution still requires restricted delegation.

### Lifecycle eligibility and freshness

`ExternalIdentityLifecycleManager.requireActive(identity)` returns the bound
Concord user ID only when the provider is registered, the binding refers to an
`EXTERNAL` user, neither Concord disable flag is set, and the source snapshot is
fresh and `ACTIVE`. It never provisions users or enables an account. Missing
bindings, missing providers and disabled accounts fail before a provider fetch.
Newly provisioned accounts therefore remain unusable until deliberately enabled
by an administrator; this slice adds no new enablement API.

`externalIdentity.maxSnapshotAge` defaults to **15 minutes** and must be positive;
invalid configuration fails server initialization. Snapshots younger than that
age can be reused. At exactly the age limit, or with missing source metadata or
a future timestamp, verification refreshes on demand. A fresh `INACTIVE` or
`NOT_FOUND` snapshot is denied without a fetch; explicit refresh or expiry allows
the provider to report reactivation. Provider removal denies even a fresh cache.

Binding, account flags, source state and database time are read in one query.
Freshness uses the database clock that timestamps successful synchronization,
not the server node's clock. After a required refresh, verification rereads all
state and rejects a replaced binding, newly disabled account, non-active source
or still-unusable snapshot. Refresh failure is propagated; the invocation does
not fall back to the old cache or another identity. Other callers may reuse a
previously successful snapshot only while it is still within the configured
freshness window. Source-side revocation can therefore remain cached for up to
that window.

This is a point-in-time eligibility check, not an authorization capability or
permission to use the app's accumulated resource grants. The returned user ID
must not be treated as an execution permit or persisted as proof of continuing
eligibility. Only resolved GitHub actor lookup is wired to it so far. External
execution, API-key authentication and internal impersonation remain blocked.
New, child and restored execution contexts still need lifecycle revalidation
and restricted-delegation enforcement before those gates can be removed.

### Delegation policy foundation

The server's `ExternalIdentityDelegationManager` loads
`externalIdentity.delegations` at startup, independently of workflow
configuration. The default empty list denies selection for every integration.
Each policy binds one exact external identity reference to Concord organization,
project and repository UUIDs. No hostname, username, project name, wildcard,
group membership or event-body field can select a broader policy. Selection
requires all binding components to match; policies are never combined.

Each entry requires an `id` (1 to 128 characters), `identity` (`providerId`,
`authority`, `subjectId`), `orgId`, `projectId`, `repositoryId`,
`projectAccessLevel`, `secrets` and `jsonStores`. Resource lists can be empty, but
must be explicit. Each resource entry contains `id` and `accessLevel`. Access
levels are `READER`, `WRITER` and `OWNER`, following Concord's existing ordering.
All UUIDs must use the canonical hyphenated representation. Unknown fields,
missing values, invalid levels, duplicate policy IDs, duplicate bindings and
duplicate resource IDs within a resource kind fail server initialization.
The distribution configuration contains a complete commented example.

`requirePolicy(identity, orgId, projectId, repositoryId)` returns an immutable
policy or throws `UnauthorizedException`. Its checks allow only the bound source
project and individually listed secret and JSON-store UUIDs, at or below their
configured caps. Listing a project does not implicitly include any secrets or
stores. Resource kinds are independent. There is no cross-project process
delegation, even for an app with grants in multiple projects.

These are **ceilings, not RBAC grants**. A later execution integration must require
both the policy and ordinary resource authorization, including visibility,
project-scoped secret restrictions and project execution mode. Policy `OWNER`
does not imply administrative or global permissions. Unmodeled resource types
and operations must remain denied for delegated execution until deliberately
supported. Do not copy policies from process arguments or serialize a policy as
an irrevocable authorization capability.

This slice provides policy storage, exact selection and standalone checks only.
It does not yet invoke selection from GitHub triggers, install resource-manager
interceptors, persist execution scope or enforce inheritance through child,
resume and session contexts. External execution remains blocked; defining a
policy does not enable it. The next enforcement slice must obtain the integration
UUIDs from validated Concord records, retain a trusted scope outside editable
workflow state, prevent scope expansion and recheck current policy on use.
Configuration changes currently require server restart, with consistent
configuration across all nodes.

### GitHub event integration

Plugins register `ExternalEventIdentityResolver` using Guice's
`Multibinder.newSetBinder(binder, ExternalEventIdentityResolver.class)`. With any
resolver registered, `github.externalIdentityAuthority` must be explicitly
configured (1 to 512 characters), or server initialization fails. This version
supports one canonical GitHub authority per webhook endpoint. Keep its signing
secret exclusive to that authority; multiple independently authenticated
authorities require separate ingress support, not routing by payload hostname.

For `useInitiator: true`, resolvers run before the user mapping cache,
LDAP DN, email and login paths. Only all-`Unhandled` outcomes permit those paths.
Null results, rejection, verification errors, authority mismatches and multiple
claims fail the initiator lookup rather than selecting a different identity.
Resolvers receive the original immutable request body captured by the
authentication filter, not the subsequently parsed or enriched event map.
Authentication mode comes from the credential path actually accepted by that
filter, never from signature-header presence alone. Repository-token deliveries
remain `ENDPOINT_CREDENTIAL` even if they include a signature header.

A resolved external actor must pass lifecycle verification, including on-demand
refresh when required, and is then still rejected by the external execution
gate. It is not automatically provisioned or enabled. Cached mappings to
`EXTERNAL` users also fail, including when no resolver is installed.
`useInitiator: false` does not invoke actor resolution and retains the existing
webhook-principal behavior. With no resolver installed, user-account resolution needs
no new configuration and preserves its existing behavior. This path includes
LDAP service accounts, not just people. User-account resolution remains a
first-class, supported path alongside plugin-managed external actor resolution.

Initiator failures follow the existing trigger executor's logged-error and
skip-trigger handling; the webhook response may still be `ok`. A successful
webhook delivery response is not proof that a process started.

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
4. GitHub actor-resolution integration, preserving user mappings and
   `useInitiator` behavior.
5. Lifecycle eligibility and on-demand freshness checks.
6. Server-controlled delegation policy selection and access ceilings.
7. Restricted delegation and lifecycle revalidation for new, child and restored
   execution contexts. A shared app's accumulated team grants must not become
   unrestricted authority for tenant-controlled workflow code.
8. API and console representation without ordinary manual external provisioning.

Intermediate slices must not enable execution as an external identity before
the required lifecycle and authorization enforcement is in place.
