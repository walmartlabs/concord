# Change log

## [Unreleased]

### Added

- runtime-v2: add grammar for `repositoryInfo` conditions in
GitHub triggers;
- concord-server: new GitHub trigger condition
`repository.enabled`. Allows filtering by the repository's
enabled/disabled state;
- runtime-v2, concord-console: record `post` events for failed
tasks, highlight failed tasks on the events tab.

### Changed

- concord-console: add scrolling to the last error popup, trigger
information popup and the task call details popup;
- concord-repository: preserve the logger's MDC when logging
Git client operations;
- concord-imports: additional logging when processing `imports`;
- concord-repository: the git client was reworked to better
support partial fetching of repositories;
- runtime-v2: unwrap runtime exceptions produced by expressions;
- concord-client: tidy up the error logging - don't log a
separate `WARN` message when the server responds with an error;
- runtime-v2: save original stacktraces when throwing a
`MultiException` (e.g. in `parallel` situations);
- concord-console: now users should be correctly redirected to
their destination page after login.



## [1.74.0] - 2020-11-24

### Added

- concord-server: allow "any of [list]" conditions when matching
process `requirements`;
- concord-console, runtime-v2: a button to launch the form wizard
from the process log tab;
- concord-console, runtime-v2: link to download individual log
segments;
- concord-console, runtime-v2: individual form links.

### Changed

- concord-server, runtime-v2: correctly remove submitted forms;
- runtime-v2: `set` now supports references to partially evaluated
values (e.g. variables defined in the same `set` block), including
nested keys;
- concord-server: add transaction-aware versions of
`ProjectManager#get` and `ProjectAccessManager#assertAccess` methods;
- runtime-v2: now only resuming threads receive the resume event's
payload. This fixes the issue with multiple `parallel` forms missing
submitted data;
- concord-agent, runtime-v2: make the runner responsible for log
segment creation;
- runtime-v2: fixed an issue when submitting a form can cause other
unsubmitted forms to dissappear;
- runtime-v2: fixed compilation of block steps when `error` is
used.

### Breaking

- runtime-v2: `set` with nested keys now replaces the top-level
reference. If `set` is called in a flow, the changes to nested data
won't be visible in the caller flow unless `out` is used;
- policy-engine, concord-server: rename `entity` policy's types for
consistency.



## [1.73.0] - 2020-11-15

### Added

- concord-console: `entryPoint` can now be used as a column in
the process list.

### Changed

- runtime-v2: fixed an issue when a parent process checkpoint
was incorrectly applied to the process' forks;
- concord-tasks: 'kill' shouldn't error on empty instanceId lists;
- concord-tasks: fix common parameters not being inherited by
`forks` in the runtime-v2 version of the `concord` task;
- runtime-v1, runtime-v2: avoid reading partially written
`instanceId` files;
- concord-server: allow null values when merging policies;
- runtime-v2: fixed the merging of multiple Concord YAML files
(e.g. `configuration.events` blocks and others).



## [1.72.0] - 2020-11-09

### Added

- concord-console: the process list now support boolean values in
custom columns and filters;
- runtime-v2: save `lastError` in process metadata (feature parity
with the runtime v1);
- policy-engine, concord-server: the `entity` policy now supports
repository objects;
- concord-console, concord-repository: support for OAuth (personal)
tokens for Git authentication.

### Changed

- concord-console: load add log segments on every refresh (temporary
workaround);
- runtime-v2: fix the loop frame being added to the call stack on
every step of `withItems`;
- runtime-v2: fixed an issue preventing form `values` from being
visible in `data.js` (custom forms);
- project: update Maven Wrapper to 0.5.6;
- runtime-v2: fixed evaluation of literals values for empty
collections (e.g. `[]` used with `set` or in expressions);
- concord-server: improved error messages when processing `imports`.



## [1.71.0] - 2020-11-03

### Added

- oneops: record incoming events in the audit log;
- runtime-v2: initial support for `parallelWithItems`;
- runtime-v2: support for expressions in `out` blocks of task
calls, flow calls, `parallel` and `expr` blocks;
- runtime-v2: save the current thread ID in `TaskResult`;
- agent-operator: add `requirements` filter;
- concord-console: the login form now provides a link to the API
key login form.

### Changed

- concord-tasks: fix the missing injection annotations in the v2
version of the `jsonStore` task (makes it useable in v2 again);
- concord-server: `ProcessKeyCache` no longer caches misses;
- concord-server: fixed handling of `any` conditions in
the `github` trigger's `files` filter.

### Breaking

- runtime-v2: new version the `TaskResult` structure, now itcan be
used to tell the runtime to suspend the process.



## [1.70.0] - 2020-10-23

### Added

- concord-server: expose task failures  in metrics (including error
messages);
- concord-server: new GitHub trigger `condition` - `files.any`.
Contains all `added`, `deleted` or `modified` files in a `push`
event.

### Changed

- runtime-v2: add support for expressions in `name` blocks for all
types of steps;
- concord-server: fixed some edge cases when converting timezones
in process status history;
- runtime-v2: automatically convert values of out variables in
scripts to their Java counterparts - Maps, Lists, etc;
- runtime-v2: fixed a NPE when tasks return `null` instead of
`TaskResult`;
- concord-cli: print out the error's stacktrace if `verbose` is
enabled.



## [1.69.0] - 2020-10-15

### Added

- runtime-v2: steps can now specify `slf4j` logging level using
the `meta.logLevel` property.

### Changed

- concord-server: timestamp values in process status history are now
returned with a correct time zone;
- concord-server: throw an exception if the resuming process wasn't
actually `SUSPENDED`;
- runtime-v2: `SLF4JPrintStreams` messages should no longer appear
in log segments;
- concord-server: task scheduler improvements - save the last error
into the DB, simplify polling, improved task heartbeat/stall checks;
- concord-server: escape expressions (`${...}`) in the input data
when resuming processes using
`/api/v1/process/{id}/resume/{eventName}` endpoint;
- ansible: fixed an issue preventing Kerberos authentication from
working in nested Docker containers (i.e. when `dockerImage` is
used);
- concord-server: allow non-admins to search audit logs by `eventId`
(`/api/v1/audit?eventId=...`);
- concord-console: fix widths of columns on the secret list page;
- runtime-v2: `if` steps now treat `null` values as `false`;
- concord-server: allow teams with no members (i.e. teams with LDAP
groups only).



## [1.68.0] - 2020-10-05

### Added

- concord-server: a PayloadBuilder method to add files to the process'
`workDir`;
- file-tasks: initial version.

### Changed

- slack-tasks: read default variables from context instead of
`@InjectVariables`. This fixes an issue of calling `slack` using
expressions;
- runtime-v2: fixed handling of process state snapshots. Now
the runtime correcly resumes from state snapshots and forks.



## [1.67.0] - 2020-10-01

### Added

- variables-tasks: runtime-v2 support for the `vars` task;
- runtime-v2: allow profiles to override flows, forms;
- concord-server: tx aware OrganizationManager's `createOrUpdate` and
`createOrGet` methods, useful for server-side plugins;
- concord-server: tx aware SecretManager's `createBinaryData` method;
- concord-console, runtime-v2: the process events list now includes
links to the source code of steps;
- resource-tasks: `prettyPrintYaml` method to output data as
formatted YAML;
- concord-server, concord-console: option to download "the
effective YAML" - a single YAML document with all Concord resources
for a given process.

### Changed

- concord-server: fixed the handling of the `values` field when
`readonly` form fields are used;
- runtime-v2: fixed a typo in code that prevented default form field
values from working correctly;
- concord-tasks: `repositoryRefresh` task was converted to v2;
- concord-agent: tone down logging when the main thread is
interrupted;
- ansible: allow any Java `Collection` types (list, set, etc) as
inventory host lists;
- concord-agent: improved error handling in workers;
- concord-server: fixed the error message template when validating
forms;
- concord-server: fixed a potential race when registering internal
metrics. Fixes a `ConcurrentModificationException` when Server is
running in an embedded environment, e.g. testcontainer-concord's
LOCAL mode.

### Breaking

- runtime-v2: `ProcessDefinition#configuration()` is a separate type
now;
- runtime-v2: move `ProjectInfo` classes to the SDK.


## [1.66.0] - 2020-09-17

### Added

- concord-server: optional `startAt` filter in
the `/api/v2/process/requirements` endpoint;
- concord-cli: support for creating new secrets using SecretManager's
`create*` methods;
- http-tasks: `followRedirects` now works for `POST` requests too;
- concord-server: access changes for projects, secrets and JSON
stores are now recorded in the audit log (e.g. assigning a team to a
project);
- concord-server: configurable out variables mode for projects. Now
project owners can restrict who can specify `out` variables in the
process request;
- slack: full support for runtime-v2. All v1 actions should now be
available in the v2 version too;
- runtime-v2, concord-console: record and display task results (as in
`configuration.events.recordTaskOutVars`).

### Changed

- concord-server, concord-console: don't send `WWW-Authenticate` for
unauthorized UI requests to prevent the basic auth popup from
showing;
- concord-server: `dateTime` form fields are now converted into UTC
on submit;
- agent-operator: when checking the queue's status, filter out
processes with `startAt` in the future;
- docker-images: python 2 to 3 migration, ansible 2.8 by default;
- ansible: python 2 to 3 compatibility fixes;
- concord-server: fixed the checksum of the `170000` changeset;
- concord-server: fixed an issue of the `runtime` parameter not being
passed correctly to the process' forks.



## [1.65.0] - 2020-09-09

### Added

- concord-console: hyperlinks to individual log segments so
users can share/access a segment instead of scrolling;
- concord-server: support for added/removed/modified `files`
in GitHub push trigger conditions;
- runtime-v2: support nested variables in
`configuration.events.inVarsBlacklist`/`outVarsBlacklist`;
- concord-agent: option to keep the workDir in a configured
directory after the process ends;
- concord-console: allow changing owners of secrets;

### Changed

- concord-console: keep the "system" segment open by default
so users can see overall progress;
- concord-server: `USERS.IS_ADMIN` column is deprecated and
its usage has been removed from the code;
- runtime-v2: working process checkpoints support.



## [1.64.0] - 2020-09-02

### Added

- runtime-v2: support for expressions in checkpoint names.

### Changed

- runtime-v2: improved error message when trying to assign a
non-serializable value into a `TaskResult`;
- concord-console: increase the number of visible log segments
to 100 (was 30);
- concord-server: change the wording of the maximum number of
dependencies error message;
- runtime-v2: merge EventConfiguration when loading multiple
resources. Allows users to specify the `events` configuration in
any Concord YAML file available for the process;
- concord-server: fixed the maximum number of dependencies error
message;
- runtime-v2: create a single merged `event` configuration from
`event` sections of all loaded Concord YAML files.

### Breaking

- runtime-v2: rename `SecretParams#name` to `secretName`. Affects
the secret creation methods in `SecretService`);
- runtime-v2: assume `retry.delay` is in seconds (like in v1).



## [1.63.0] - 2020-08-30

### Added

- concord-imports: support for `dir` imports (external directories),
disabled by default;
- crypto-tasks: an action to create new secrets (runtime-v2 only);
- concord-server: JSON store data can now be updated by either `POST`
or `PUT` method;
- runtime-v2: support for nested `set`;
- runtime-v2: support for `out` variables in `try` and `block`
steps;
- runtime-v2: support for naming of `log`, `throw` and `expr` steps;
- runtime-v2: additional `Variables#assert*` methods.

### Changed

- runtime-v2: fixed a potential NPE in the `retry` code when handling
exceptions w/o message;
- docker-images: remove `dumb-init` as Docker ships its own init
implementation;
- runtime-v2: when using `withItems`, `out` variables are now
collected into lists;
- concord-server: remove user's LDAP groups from the DB when
the account is disabled;
- runtime-v1: fixed an issue when GitHub triggers without `version`
were treated as v1 triggers;
- concord-console: new look for the runtime v2 log viewer;
- concord-console: fix layout of the JSON store query editor;
- iam-sso: mark the SSO cookie as "secure";
- ansible: fixed an issue when `ignore_error` values were saved
"as is" in Ansible events (e.g. unevaluated expressions);
- concord-agent: do not print out the process' `sessionToken` in
logs;
- concord-console: fix manual refreshing of the process list;
- concord-server: allow nested metadata filters in
`GET /api/v2/process` and `GET /api/v2/process/count` endpoints;
- concord-server: runtime-v2 compatibility for processes started via
the browser link (aka "the process portal").

### Breaking

- concord-server: the process list entries
(`GET /api/v2/process` endpoint) no longer include `imports`.



## [1.62.0] - 2020-08-19

### Added

- concord-server: a way to deploy the DB schema without
`SUPERUSER` role and/or `CREATE EXTENSION` privileges;
- concord-console: a way to open a process log at a specific
checkpoint by using `#/process/{id}/log#{eventCorrelationId}`;
- runtime-v2: the v2 SDK now provides the `DependencyManager`
interface.

### Changed

- concord-client: `ClientUtils#getHeaders` now correctly assumes
case-insensitivity of header names;
- concord-server: `OrganizationManager#create` and `#update`
replaced with a single `createOrUpdate` method;
- ansible: when using `auth.privateKey.path` don't remove the file
after the play's end.

### Breaking

- runtime-v2: `Task#execute` and `ReentrantTask#resume` now return
a new common result type - `com.walmartlabs.concord.runtime.v2.sdk.TaskResult`.



## [1.61.0] - 2020-08-13

### Added

- concord-agent: `CONCORD_JAVA_OPTS` environment variable can now be
used to specify additional JVM options.

### Changed

- concord-server: allow `null` values in process event data to
improve backward compatibility with older plugins;
- runtime-v2: warn about `@Singleton` tasks;
- runtime-v1, runtime-v2: `throw` with a string value doesn't
produce a stacktrace anymore;
- project: JDK 11 compatibility.

### Breaking

- runtime-v2: `ReentrantTask` now accepts `ResumeEvent` instead of
`Map<String, Object>`;
- runtime-v2: `@DefaultVariables` annotation was replaced with
`Context#defaultVariables()` method;
- runtime-v2: the SDK module no longer shares interfaces/types with
the v1 SDK:
  - `ApiConfiguration`
  - `DockerContainerSpec`



## [1.60.1] - 2020-08-05

### Changed

- concord-agent: fixed an issue preventing the Agent from working
in the `LOCAL` mode of [testcontainers-concord](https://github.com/concord-workflow/testcontainers-concord).



## [1.60.0] - 2020-08-05

### Added

- runtime-v2: support for `name` attributes in task, flow call and
expresion steps;
- concord-server: store process `dependencies` in
the `process_queue.dependencies` column;
- runtime-v2: `LockService` implementation;
- concord-server: expose the current number of processes with
"wait conditions" as a new metric - `process_queue_enqueued_wait`.

### Changed

- concord-agent: use process session token to append logs, download
state, update status, etc. Previously, agents used an API token from
the configuration file for such operations;
- concord-console: the refresh action of the process list page was
rewritten to use React Hooks;
- concord-console: fixed scrolling on the repository list page in
the presence of a modal dialog.



## [1.59.0] - 2020-07-30

### Changed

- concord-console: improved error handling, standartize refresh
indicators/buttons;
- concord-server: fixed the parsing of `process.maxStateAge`
configuration parameter.

### Breaking

- concord-server, concord-server-sdk: the `ProcessKey` interface
was replaced with a concrete type, moved from the server's `impl`
module;
- concord-server: remove the support for GitHub triggers v1.
The v1 triggers are deprecared since 1.32.0+.


## [1.58.1] - 2020-07-28

### Changed

- concord-server: count child processes towards the concurrency
limit;
- agent-operator: now correctly calculates the pod's
configuration hash, fixes an issue when pods where incorrectly
scheduled for replacement due to the pool's size changes;
- concord-server: perform all process key lookups via
`ProcessKeyCache`;
- concord-console: disable autocomplete for password fields on
the new secret page and when encrypting a string.

### Breaking

- concord-agent, concord-server, concord-cli: the configuration
parameters that previously used integers for duration values
(e.g. `repositoryCache.maxAge`) now use literal duration values.



## [1.58.0] - 2020-07-24

### Added

- concord-console: host status filter to the Ansible host list
page;
- runtime-v2: support for the `debug` mode.

### Changed

- runtime-v2: improved validation of the `script` step;
- concord-tasks: log the child process' URL;
- concord-cli: by default do not remove the `target` directory
after the process finishes;
- concord-cli: trim whitespace when reading single-value (string)
secrets;
- concord-server: fixed an issue preventing JSON store creation
requests from being correctly validated (i.e. disallow invalid
store names).

### Breaking

- concord-server: `java.util.Date` and `java.sql.Timestamp` used
in internal and external Java APIs are replaced with
`java.time.OffsetDateTime`;
- concord-server-db: `timestamp` columns are migrated to
`timestamptz`. The migration procedure requires the DB user to
have `SUPERUSER` privileges for the duration of the migration.



## [1.57.0] - 2020-07-22

### Added

- concord-server, concord-repository: an option to ignore
remote fetch if the local commit ID is the same as the remote
commit ID;
- concord-server: audit logs for API key creation and removal;
- policy-engine, concord-server: new policy `state`. Allows
control of various aspects of process state (size, filenames,
etc);
- runtime-v1: new option configuration option
`runner.enableSisuIndex`. Enables usage of the Sisu bindings
index;
- concord-server: creation of organizations now requires a
role with the `createOrg` permission.

### Changed

- concord-server: fixed an issue preventing the validation of
the request data from working correctly in JSON Store
endpoints;
- concord-repository: skip fetch if the local copy points to
the same commit;
- concord-repository: correctly release the repository's lock;
- concord-server: fixed a bug in the "process portal" page
template which was preventing the page from reloading;
- agent-operator: the API client now saves session cookies to
avoid the need to authenticate on every request;
- concord-server: allow users with the admin role to create
new LDAP-based accounts even if the user creation is disabled
for the realm;
- concord-task: fixed an issue preventing subsequent calls to
the task to fail when the suspend/resume feature is used;
- runtime-v2: now requires `sisu-maven-plugin` to be used in
all plugins.



## [1.56.0] - 2020-07-09

### Added

- concord-cli: initial support for `profiles`;
- concord-cli: option to remove the target directory before
the process starts;
- concord-cli: provide feedback while processing `imports`;
- concord-cli: the `run` command now supports profiles;
- dependency-manager: support for proxy servers (http/https);
- runtime-v2: support for `meta` attributes in `log` steps.

### Changed

- concord-server: fixed an issue with process wait conditions not
being cleared in time;
- concord-server: fixed a potential NPE in the repository cache
cleanup code;
- project: JDK11+ compatibility;
- concord-server: force all usernames to lower case to avoid issues
with AD/LDAP authentication and environments with multiple auth/z
providers.



## [1.55.0] - 2020-07-01

### Added

- runtime-v2: warn when a variable name "shadows" a task name;
- concord-server: lift restrictions on the format of usernames.
Allow `@` and other special characters;
- concord-server: a short project cache for GitHub triggers v1 to
avoid multiple DB queries when filtering out triggers;
- concord-tasks: runtime v2 compatibility;
- concord-server, agent-operator: fetch only process requirements,
configurable fetch depth;
- concord-server: new policy to control size of process attachments.

### Changed

- agent-operator: new CR template property `%%preStopHook%%` for
injecting the preStop hook script's content;
- concord-cli: `run` action now copies all files into the `target`
directory in the current directory;
- concord-client: the error message when the requested secret type
doesn't match the result now includes the name of the secret;
- policy-engine: no longer dependens on `commons-lang3`;
- concord-console, concord-server: `ENQUEUED` filter now includes
processes with `start_at < now`;
- concord-server: stop all background and scheduled tasks when
the maintenance mode is enabled;
- concord-repository: move cache directories before removing to
reduce the time spent while locked;
- concord-server: fix process status update in the queue batching
task;
- concord-server: optimize the session key validation by reducing
the amount of data pulled from the DB.



## [1.54.0] - 2020-06-24

### Added

- concord-console: an indicator for partially loaded log segments;
- runtime-v2: initial support for parallel block's in/out;
- runtime-v2: initial support for in/out variables in `parallel`
blocks;
- runtime-v2: initial support for "reentrant tasks" (tasks that can
be suspended and resumed);
- agent-operator: add the preStop script to the `dind` container;
- concord-server: additional metrics for github events - the number
of processes started/triggers fetched/etc per event;
- concord-console: new loading indicator for log segments;
- runtime-v2: add `projectInfo` to `context`;
- concord-cli: option to run individual Concord YAML files.

### Changed

- concord-server: when updating/moving a project assert name when no
ID given;
- concord-server: fixed the calculation of
the `enqueued-workers-available` metric;
- concord-console: updated Json Store capacity indicator;
- concord-console: auto refresh the child process list;
- concord-console: show open segments correctly when the system
segment's visibility changes.



## [1.53.1] - 2020-06-16

### Changed

- concord-server: fixed an issue preventing `useInitiator` from
being correctly handled by GitHub triggers.



## [1.53.0] - 2020-06-13

### Added

- concord-server: option to ignore "empty" `push` notifications
from GitHub;
- concord-server: optional batching mechanism for the `NEW` to
`ENQUEUED` transition;
- runtime-v2, concord-console: show `meta.segmentName` as the log
segment's name;
- concord-console: show GitHub event details in the "triggered by"
popup;
- docker-tasks: runtime v2 compatibility;
- runtime-v2: option to redirect `System.out` and `err` to segmented
logs;
- runtime-v2: support for `error` blocks in `call` steps;
- runtime-v2: `Context` can now be injected into tasks.

### Changed

- concord-repository: fixed an issue preventing `imports` from 
being excluded from the process state;
- agent-operator: use `preStop` hooks instead of directly
removing pods;
- concord-server: OneOps specific endpoints extracted as a server
plugin;
- concord-console: re-load repository data when the process start
popup opens;
- ansible: improved error messages in lookup plugins for Concord
secrets;
- concord-console: the team list page and the "find a team" dropdown
are rewritten to use React Hooks instead of `react-redux`;
- runtime-v1: trim data when recording in/out variables;
- concord-console: the trigger list moved to the repository page
into a separate tab;
- concord-console: the login form was rewritten to use React Hooks
instead of `react-redux`;
- runtime-v2: `call` can now have multiple `out` variables;
- concord-server: the endpoint for creation of Inventory queries now
supports both `text/plain` and `application/json` content types;
- concord-server: disable key validation when uploading existing
SSH key pairs. The library (JSch) used to validate keys doesn't
support keys larger than 2048 bits.

### Breaking

- runtime-v2: the `Task` interface now accepts input `Variables`
instead of the context.



## [1.52.0] - 2020-06-03

### Added

- runtime-v2: `throw` step support;
- runtime-v2: record pre/post events for task called using
expressions;
- runtime-v2, concord-console: show the YAML file name on
the events tab.

### Changed

- concord-server: fix the adding of team members by user IDs;
- agent-operator: the autoscaler now scales up more rapidly and
scaled down more gradually;
- slack: reduce chattiness in the process log by moving some
of the log statements to `debug`;
- runtime-v2: replace custom service injector with Guice-based
injector;
- runtime-v2: more details when recording step events;
- runtime-v2: fix logging for steps without "proper"
segments - scripts, expressions, etc;
- runtime-v2: start the heartbeat as soon as possible;
- runtime-v2: support for the "short form" of task calling
has been removed;
- concord-server: re-initialize the process initiator when
creating a fork. This fixes an issue when a process fork is
created using an API key that belongs to a user other than
the parent process' current user.

### Breaking

- runtime-v2: support `@InjectVariable` annotations has been
removed.



## [1.51.0] - 2020-05-27

### Added

- runtime-v2: implement process heartbeat;
- concord-console: custom columns in the process list can now
use `requirements` as the `source`;
- runtime-v2: support for `error` blocks in `task` steps;
- dynamic-tasks: runtime v2 compatibility;
- concord-console: new "Duration" column in the process list.
Shows the amount of time spent `RUNNING` a process;
- noderoster-tasks: runtime v2 compatibility.

### Changed

- concord-console: `ENQUEUED` processes that are scheduled for
future (using `startAt`) can now be displayed separately from
regular `ENQUEUED` processes and vice versa;
- ansible: escape special characters when the command script
is created;
- concord-server: fixed an issue preventing the LDAP group
synchronizer from working correctly if there are previously
disabled user accounts;
- concord-console: use IDs when adding users to teams to avoid
issues with duplicate usernames or the need for `userType`
parameter. Allow `cron` and other system users to be added to
teams using the UI.

### Breaking

- runtime-v2: make all variables local. Variables must be
explictly passed between flows using `in` and `out` parameters;
- docker: rebase images on top of `centos:8`.



## [1.50.1] - 2020-05-19

### Changed

- concord-server: fix the "Unconnected sockets not implemented"
issue when connection timeout is used for LDAP connections. 



## [1.50.0] - 2020-05-18

### Added

- concord-server: configurable `connectTimeout` and
`readTimeout` for LDAP calls;
- concord-server: log duration of GIT operations;
- runtime-v2: support for return and exit steps;
- ansible: new parameters to enable or disable various features:
`enableEvents`, `enableStats`, `enableOutsVars`;
- runtime-v2: support for expressions in call step;
- runtime-v2: grammar support for cron trigger's `timezone`;
- concord-cli: support for `crypto.exportAsFile`;
- runtime-v2: `template` support;
- runtime-v2: support for out variables;
- repository: support for "non detached" checkouts;
- runtime-v2: support for setting variables using `set`.

### Changed

- iam-sso: re-enable disabled user accounts on successful login;
- concord-server: fix handling of `Bearer` tokens;
- runtime-v2: in/out params in task events are now truncated to
limit the amount of data saved;
- runtime-v1: process metadata updates are now automatically
retried in case of errors;
- runtime-v2: add `concord/concord.yml`to the list of default
`resources`;
- runtime-v2: replace Jackson's `JsonLocation` with a custom type;
- runtime-v2: log step execution errors with the step's location;
- runtime-v2: the default expression evaluator now implements
different evaluation rules for process arguments, task inputs and
`set` steps.



## [1.49.0] - 2020-05-06

### Added

- concord-cli: support for `imports`;
- concord-cli: allow specifying an `entryPoint`;
- concord-cli: support for `crypto.exportAsString`;
- concord-cli: initial support for `crypto.decryptString`;
- concord-cli: initial support for "default variables". The built-in
variables provide some useful defaults for the Ansible plugin;
- runtime-v2: support for process metadata;
- runtime-v2: support for "processTimeout", "exclusive", "events"
and "requirements" elements;
- runtime-v2: allow process suspension via `Context#suspend` method;
- concord-server: show a custom 404 page when a form is not found;
- misc-tasks, datetime-tasks: runtime v2 compatibility;
- lock-tasks: runtime v2 compatibility;
- ansible: runtime v2 compatibility;
- locale-tasks: runtime v2 compatibility;
- smtp-tasks: runtime v2 compatibility;
- concord-server, concord-console, concord-agent, runtime-v2:
initial implementation of "segmented" process logs. Each task now
gets a separate segment of the process log and can be shown on
the UI individually;
- log-tasks: add `level` parameter to the v2 version of the task;
- concord-agent: configurable default JVM parameters;
- concord-console: show `startAt` to the process toolbar;
- concord-server: make the embedded Jetty aware of proxy headers
such as `X-Forwarded-For`;
- concord-server: audit log now includes API key IDs.

### Changed

- runtime-v1: fixed an issue preventing process variables from being
passed into an `onFailure` handler if the parent process fails with
an unhandled exception;
- concord-agent: log dependency check results using `WARN` level;
- runtime-v2: fix github trigger's exclusive attribute definition;
- ansible: improved detection of the `setup` task type;
- project: replace ollie dependency with ollie-config;
- concord-server: fix the "show only user's organizations" toggle;
- concord-console: don't do the initial search when the Node Roster
page opens;
- concord-server, concord-console: fixed the behaviour of
the "show only user's organizations" toggle on the organizations
list page.



## [1.48.1] - 2020-04-22

### Changed

- concord-server: fixed an issue when the `requestInfo` variable
isn't provided in some cases (regression);
- concord-server: fixed an issue when the `projectInfo` variable
is overwritten with an incorrect value when the process is resumed
(regression).



## [1.48.0] - 2020-04-21

### Added

- kv-tasks: runtime v2 compatibility;
- resource-tasks: runtime v2 compatibility;
- crypto-tasks: runtime v2 compatibility;
- runtime-v2: optional support for "segmented logs" where each
task call gets its own log file;
- log-tasks: runtime v2 compatiblity;
- throw-tasks: runtime v2 compatibility;
- runtime-v2: support for `resources`;
- runtime-v2: support for `script` steps.

### Changed

- concord-server: public organizations are now visible for
everyone regardless of membership;
- concord-server: organization owners can now see their
organizations even if they don't belong to any team in it;
- concord-server: fixed the even type filter when querying
repository events;
- policy-engine, concord-server: remove deprecated and unused
`queue` policies - `process`, `processPerOrg` and
`processPerProject`;
- runtime-v2: improved "method not found" error messages when
evaluating expressions;
- runtime-v2, concord-server: improved detection of
the `runtime` parameter. Now it can be specified in
the `configuration` section or in the request parameters;
- runtime-v2: the expression evaluator now correctly supports
partial evaluation of nested data;
- concord-server: some endpoints that were previously
automatically creating users no longer do so. E.g. when
specifying an owner of a JSON store, the user record must
exist beforehand;
- concord-project-loader: allow `runtime` to be specified
externally, e.g. in process request parameters;
- ansible: improved validation of `inventory` and
`vaultPassword` parameters;
- concord-server: make the "Copying the repository's data"
message shorter, don't print out the repository's metadata;
- concord-server, concord-agent, runtime-v1, v2: major
process/runner configuration refactoring. The process' session
token is no longer saved as a file in the working directory,
but passed as a process configuration field;
- repository: removed delay between `fetch` retries.



## [1.47.1] - 2020-04-10

### Changed

- concord-agent: fix "preforking" by removing the process'
session key from runner configuration files;
- concord-server: removed old LDAP user search endpoint
`/api/service/console/search/users` and all associated code.



## [1.47.0] - 2020-04-08

### Added

- runtime-v1, runtime-v2, concord-server: support for
`publicFlows` - a top-level element with a list of public flows.
Only public flows are allowed to be used as `entryPoint` values;
- concord-server: additional audit logging for user
management - account creation/update, enabling or disabling
of the account;
- iam-sso: option to convert user domain names via
`sso.domainMapping` configuration parameter;
- http-tasks: initial support for runtime v2.

### Changed

- concord-server: improved validation of API key names, improved
handling of duplicates;
- runtime-v2: now the runtime evaluates only top-level variables
in task, form and flow call parameters to avoid undesirable
"interpolation" of nested values;
- concord-server: when executing `ldap.principalSearchFilter`
pass username and domain values separately in additional
arguments;
- concord-agent: the start script no longer depends on `uuid`
executable.



## [1.46.0] - 2020-04-02

### Added

- runtime-v2: support for `if` and `switch` steps;
- runtime-v2: support for lists in `githubOrg` and `githubRepo`
conditions in `github` triggers;
- runtime-v2: ability to save all task call results in the process
state for later use. Useful for implementing policies that restrict
flow execution based on results of previously called tasks;
- concord-server: automatically re-enable AD/LDAP user accounts
that were previously disabled by the AD/LDAP synchronization
process;
- runtime-v2: `DockerService` support;
- runtime-v2: support for task policies.

### Changed

- concord-server, concord-agent: externalize default git operation
timeout duration. Increase default value to ten minutes;
- concord-server: treat empty project name as null when updating
secret. This fixes an issue preventing users from being able to
"unlink" secrets from projects.



## [1.45.0] - 2020-03-30

### Added

- runtime-v2: initial support for checkpoints;
- runtime-v2: support for multiple `TaskProvider` instances;
- runtime-v2: initial implementation of `SecretService` and
`FileService`;
- concord-server: option to generate the default admin API token on
start;
- runtime-v2: support for triggers;
- runtime-v2: process event recording;
- http-tasks: response headers are now saved into the result
variable;
- concord-console: alternate shading for actionable tables and lists;
- concord-server, concord-agent: configurable repository locks.
The maximum number of concurrent Git operations can now be configured
in the Server and Agent configuration files;
- runtime-v2: initial support for task defaults (default task
variables).

### Changed

- concord-console: some of user selection fields (e.g. a project
owner field) now perform search using the DB data, without accessing
external AD/LDAP servers;
- concord-agent: fixed a potential race condition when
the maintenance mode is activated;
- dependency-manager: disable Aether caching, allows
`snapshotPolicy.updatePolicy` to work correctly;
- concord-console: make the scroll up button always visible;
- concord-tasks: `requirements` are now correctly passed into the API
request;
- vagrant: fixed the database container startup.



## [1.44.0] - 2020-03-12

### Added

- concord-server: configurable CORS origin;
- concord-console: ability to filter processes by a repository
name;
- new server plugin: `oidc`. Allows user authentication using an
OpenID Connect provider;
- concord-tasks: `meta` field can now be specified when starting or
forking processes using the `concord` task;
- concord-cli: initial support for running v2 flows;
- runtime-v2: support for `imports`;
- runtime-v2: support for running v1 tasks.

### Changed

- ansible: no longer requires `ujson` Python module. The module is
still included into the `concord-ansible` image for backward
compatibility with older version of the Ansible plugin;
- ansible: when recording events make sure that "unsafe strings"
(`AnsibleUnsafeText`) are truncated just like regular strings. This
prevents large amount of data from getting into the event's payload;
- ansible: fix `concord_data_secret` lookup when used with Python 3;
- http-tasks: show `Authorization` header value in `debug` mode;
- http-tasks: validate the `auth.basic.token` value;
- concord-console: limit the number of rows returned when running
JSON store queries;
- concord-console: fixed the secret move popup's message;
- concord-console: fixed vertical alignment in the repository events
table.



## [1.43.0] - 2020-03-05

### Added

- concord-tasks: ability to pass `requirements` into the called
process;
- concord-server: `v2` syntax for generic and OneOps triggers;
- concord-server, concord-console: ability to move project and
secrets across organizations;
- smtp-tasks: disable debug output by default, add `debug` option;
- concord-console: show the process status in the window's favicon.

### Changed

- concord-server: fixed the raw payload mode check. Now if the
project's raw payload mode is set to "Only team members", the server
correctly looks for team members with `READER` access level or
higher;
- http-tasks: honor the `ignoreErrors` value when handling
"unauhorized" (401) responses;
- ansible: the sensitive data filter is now opt-in by default and
can be enabled with the new `enableLogFiltering` parameter;
- concord-server: fixed handling of GitHub `team` events. Now it
correctly calculates the appropriate `githubRepo` values;
- concord-server: if `startAt` value is in the past, log the current
server time in the error message;
- concord-console: make `lastUpdatedAt` available as one of
the built-in columns again.



## [1.42.0] - 2020-02-26

### Added

- concord-console: the repository edit page was redesigned to include
the new "Events" tab. Currently, the "Events" tab allows users to see
incoming GitHub events which can be used to debug trigger conditions
or monitor the repository traffic;
- concord-server: new `saveAs` parameter in the process resume
endpoint. Allows saving the received JSON body as the specified
process `configuration` value;
- misc-tasks: new `datetime.currentWithZone` methods to get
the current date/time for a specific time zone;
- concord-sdk: new `Context#interpolate(Object, Map<String, Object>)`
method to interpolate values using the specified `Map` as variables.

### Changed

- concord-server: process "wait conditions" (locks, waiting for other
processes, etc) are now processed in batches;
- concord-server: the process queue dispatcher now able to handle
multiple processes per projects at the time;
- ansible, noderoster: trim the data (host names, host groups and
task names) before inserting it into the DB;
- concord-console: the `lastUpdatedAt` column was removed from all
default process lists. It still can be used in custom column
configurations;
- concord-tasks: fixed a bug causing duplicate entries in the `jobIds`
variable when multiple forks start;
- concord-server: better validation of JSON Store query results.
The server expects single column results with valid JSON objects as
values and will report so if the query results don't pass the
validation;
- smtp-tasks: better validation of input parameters;
- concord-tasks: make sure the API response's body is closed, prevent
leaks. 



## [1.41.0] - 2020-02-13

### Added

- kv-tasks: allow calling from `script` environment;
- project-model: support for expressions in retry parameters;
- concord-server: support for non-repository GitHub events (e.g.
`team`, `organization`, etc); 
- concord-console: date/time filters for the audit log.

### Changed

- ansible: improved Python 3 compatibility;
- concord-server: fixed the `repoId`/`repoName` filter in the
`/api/v2/process` endpoint;
- concord-server: fixed a bug in the process queue dispatcher when
a process with `requirements` that cannot be satisfied could block
other processes in the same project from being dispatched to
workers;
- slack-task: allow sending messages with JSON and support updates;
- slack-task: make the `action` parameter of the `slackChannel`
task case-insensitive;
- concord-task: multiple process forks are now started in parallel.



## [1.40.0] - 2020-02-06

### Added

- concord-task: option to disable `debug` logging, including
the process arguments;
- concord-server: support for filtering on repository ID or name in
`/api/v2/process` and `/api/v2/process/count` endpoints;
- concord-console: initial version of the Node Roster UI;
- concord-console: new "Audit Log" tab on the organization,
project, team, secret and JSON store pages;
- policies, concord-server: ability to set default configuration
for processes using the new `defaultProcessCfg` policy;
- slack: new parameter `replyBroadcast`. If `true` a reply to a
thread is also posted to the channel.

### Changed

- concord-console: support for calling processes with arguments
using manual triggers;
- concord-server: improvements in audit logging of team changes.
Now the audit events contain the change's delta;
- concord-server: only JSON objects (Java Maps) are now allowed
as JSON Store items.



## [1.39.3] - 2020-01-29

### Added

- concord-agent: make the maintenance mode port configurable.

### Changed

- k8s: the example CRDs were updated to include the pod's name into
the agent's `capabilities`;
- concord-server: the process queue dispatcher now sends responses
outside of the global lock;
- concord-console: fixed "flickering" when switching between
playbooks in the new Ansible UI;
- ansible: callback plugins updated to support both Python 2 and 3;
- concord-server: the `/api/v1/process/{id}/log` endpoint now
performs additional permissions check. Now only initiators, project
`WRITERS`, admins and "global readers" can access process logs.
Disabled by default.



## [1.38.2] - 2020-01-23

### Changed

- concord-server: fixed negative maxSize in JSON store capacity
response; 
- concord-server: fixed the existence check when creating or
updating an inventory.



## [1.38.1] - 2020-01-22

### Changed

- concord-server: fixed a potential NPE when handling non-repository
GitHub events (issues, teams, etc);
- concord-server: revert changes in `/api/v1/org/${orgName}/inventory/${inventoryName}/data`
endpoints.



## [1.38.0] - 2020-01-21

### Added

- concord-server: configurable max request size for the embedded
Jetty server;
- new JSON Store API, UI and a flow task;
- concord-server: provide `event.commitId` variable for
`pull_request` and `push` events in `github` triggers;
- node-roster: initial version;
- concord-server, concord-console: disable checkpoint restoration for
suspended processes;
- concord-console: pagination to the process wait conditions page;
- dependency-manager: support for authentication and release/snapshot
policies;
- ansible: allow mix and match of inline inventories and file paths;
- concord-console: customizable pages for external resources
presented as iframes.

### Changed

- concord-server: the Inventory API is deprecated in favor of the
JSON Store API;
- concord-console: fixed pagination of the child process list;
- concord-server: the system trigger (responsible for refreshing
repositories) now correctly triggers only on "push" events;
- concord-server: when refreshing trigger definitions in the DB,
the server now correctly detects changes and updates/replaces only
the changed triggers;
- concord-console: require an additional confirmation when removing a
repository;
- concord-server: show the `available_worker` metric even if all
workers of a specific "flavor" are gone;
- concord-server: escape expressions (`${}`) in external event data.
All string values in the `event` variable (which is provided for
processes triggered by external events) will have their `${` escaped
as `\${`;
- concord-cli: fix the build, the standalone JAR is now runnable
again;
- ansible: fixed processing of events that are created by the task
called in an expression.



## [1.37.1] - 2010-01-06

### Changed

- variables-task: fixed an interpolation issue in the `set`
implementation. Now it correctly works for nested values that are
referencing variables from the "outside" scope.



## [1.37.0] - 2020-01-02

### Added

- docker: option to use the container's user instead of forcing
the default Concord UID;
- concord-console: project configuration editor;
- slack: new action `addReaction`;
- concord-server, plugins: new server plugin `kafka-event-sink`.
Sends process events into a Kafka topic;
- concord-server-sdk: new interfaces `ProcessEventListener`,
`AuditLogListener` and `ProcessLogListener`. Allows server plugins to
listen for process-level events, process logs and audit events.

### Changed

- slack: make `authToken` and `apiToken` parameters interchangeable;
- kv-tasks: disallow `null` or empty keys;
- concord-server: fixed an RBAC issue when loading extended process
event data. Now the `/api/v1/process/PROCESS_ID/event?includeAll=true`
correctly checks for org/project permissions and ownership;
- project-model-v1: merge `dependencies` lists from all loaded
Concord YAML files;
- docker: expose the host's `DOCKER_HOST` to the containers running
in the `docker` task;
- concord-console: fixed the dropdown behavior in the operation
confirmation popup;
- variables-tasks: fixed an interpolation issue when multiple
dependant values are `set` simultaneously;
- http: fixed a potential NPE on empty responses;
- concord-server, concord-console, ansible: hosts statuses are now
limited to `ok`, `failed` and `unreachable`. Fixes the host status
calculation and reduces the number of recorded host events;
- concord-server, concord-console, ansible: fixed handling of retries
in the new Ansible UI;
- concord-server, concord-console: fixed the failed hosts/tasks
request for processes with multiple playbook executions;
- concord-server: fixed a division by zero error when calculating
Ansible play progress;
- ansible: correctly handle Jinja2 expressions in host groups when
recording events; 
- concord-server: sanitize `\u0000` in strings when inserting JSONB
data (e.g. process events). 

### Breaking

- concord-agent: support for the container-per-process execution mode
is removed. It will be brought back in the future as a a separate
type of Agent;
- concord-agent: `runner` configuration section is renamed to
`runnerV1` to better support alternative runtimes.



## [1.36.1] - 2019-12-11

### Changed

- ansible: improved handling of `currentRetryCount` attributes in
playbook events. This fixes an issue that was preventing Ansible
events from being correctly processed by the server.   



## [1.36.0] - 2019-12-09

### Added

- concord-console: the about page now shows the date when the
environment was last updated (optional, requires update of `cfg.js`);
- concord-console: support for multiple profiles in the repository
run dialog;
- server: optional caching of LDAP query results;
- http: default `User-Agent` value, contains the version of
the plugin;
- imports: ability to `exclude` files when importing external
resources;
- concord-console: additional process menu links can now be specified
using the `cfg.js` file;
- ansible, concord-console: new view for Ansible runtime statistics;
- concord-server: optional rate limit for the process start
endpoints;
- slack: new option to `ignoreErrors`;
- concord-server: cache for policies;
- concord-server, concord-console: the "new project" and the "new
secret" buttons can now be disabled on the organization level using
an `entity` policy;
- concord-server, concord-agent: configurable LRU cache for GIT
repositories;
- concord-console: pagination for the project list;
- docker, ansible: automatic retries when pulling images;
- concord-server: configurable hard limit for the process log size;
- ansible: new parameter `enablePolicy` to apply Concord policies to
plays;
- concord-server: new endpoints `/api/v1/agent/all/workers` and
`/api/v1/agent/all/workersCount` to retrieve the list of currently
available agent workers and the number of available workers grouped
by a property in agent `capabilities`;

### Changed

- concord-server, concord-console: when validating repositories
return errors and warnings separately. Downgrade a missing
`entryPoint` reference in triggers to a warning;
- concord-console: project and secret visibility is now private by
default;
- concord-console: fixed missing support for `activeProfiles` for
manual triggers;
- concord-console: updated look of the process status toolbar;
- concord-server: enabled support for `onFailure`, `onCancel` and
other process handlers for forks;
- slack: better handling of invalid response codes, increase delay
between retries;
- concord-service: replace `WatchService` with simple polling to
better support the reloading of the default process configuration in
Docker environment;
- runtime-v1: move the processing of `imports` to the `ProjectLoader`;
- concord-console: better handling of undefined process metadata
values;
- runtime-v1: fixed an issue with nested `retry` blocks;
- concord-server: handle processes stuck in the `PREPARING` status;
- concord-server: fixed an issue preventing the `github` trigger's
`useEventCommitId` from working correctly;
- concord-server: upgraded to Ollie 0.0.33;
- concord-server: additional permission checks when downloading
process attachments. Now only process initiators, project owners or
admins can download attachments;
- ansible: disable Concord policies by default;
- dependency-manager: better handling of partially downloaded
artifacts;
- concord-console: show the last error icon on the process status page;
- concord-server: forks now re-use the parent process' `imports`.

### Breaking

- concord-server: the `/api/v1/process/{id}/kv/{key}/string` endpoint
now returns `Content-Type: text/plain` instead of `application/json`.
This fixes an issue with non JSON strings;
- concord-server: make the v2 the default version for `github`
triggers. The existing projects must update their `github` trigger
definitions or set the default version in the server configuration
file;
- concord-console: the image is removed. The Console files are
now served by the Server itself;
- concord-runner: remove deprecated process definition attributes:
    - `__attr_localPath`;
- concord-sdk: remove deprecated interfaces and annotations:
    - `com.walmartlabs.concord.common.InjectVariable`;
    - `com.walmartlabs.concord.common.Task`.



## [1.35.1] - 2019-10-17

### Changed

- concord-server: additional configuration settings for the handling
of suspended processes;
- concord-server: improve the asynchronous processing of external
events;
- concord-server: fix a potential issue when GitHub triggers a
process without a valid user account.



## [1.35.0] - 2019-10-16

### Added

- concord-console: the repository links that end with `.git` are now
correctly displayed on the process status page;
- concord-server: new `agent-workers-available` metric, shows how
many Agent worker slots are available;
- docker: support for capturing the command's `stderr` in addition to
`stdout`;
- http-tasks: support for `multipart/form-data` requests;
- concord-agent: support for running processes with custom JVM
parameters.

### Changed

- concord-runner: sort dependencies before loading to ensure
consistent class loading;
- concord-server, concord-console: fill-in the process status page's
"Triggered By" for processes triggered by `cron`;
- concord-agent: fix system log messages that may appear out of order
in the process log. 



## [1.34.3] - 2019-10-07

### Changed

- concord-server: ignore individual errors when refreshing multiple
repositories at once;
- concord-server-db: add missing index on
`PROCESS_CHECKPOINTS (INSTANCE_ID)`;
- concord-server: some optimizations for the Ansible event
processing;
- concord-server: optimize `GET /api/v2/process` endpoint, equality
filter on metadata fields is now much faster;
- concord-server: additional metrics for LDAP and Ansible event
processing.



## [1.34.2] - 2019-09-27

### Changed

- concord-runner: update BPM to 0.58.2, enables interpolation of
expressions in form field labels;
- concord-server: fixed an issue preventing `useInitiator: false`
from working correctly for GitHub triggers.



## [1.34.1] - 2019-09-22

### Changed

- concord-server: fixed the system trigger definition. Now it
correctly fires up on changes in all registered repositories.



## [1.34.0] - 2019-09-19

### Added

- concord-server: new endpoint `/api/v2/process/count`. Returns
the number of processes for the specified filters;
- dependency-manager: automatic retries, improved error reporting;
- concord-server: the `/api/v2/process` endpoint now supports
different types of metadata filters (`eq`, `startsWith`, etc);
- concord-server-sdk: now provides metrics annotations (e.g.
`@WithTimer`).

### Changed

- concord-agent: dependency resolution logs are now correctly
sent back to the server;
- concord-server: fix `STARTING` statuses not being registered in
the process history; 
- concord-server: the `ping` endpoint now checks for the DB
connection;
- concord-server: the repository refresh process is updated to use
GitHub triggers v2.

### Breaking

- concord-server: the `/api/2/process` endpoint now requires a
project ID or a project name to be specified when metadata filters
are used;
- concord-server: the entity policy's attribute `trigger.name` is
renamed to `trigger.eventSource`.



## [1.33.0] - 2019-09-07

### Added

- concord-runner: additional logging when the process heartbeat is
restored;
- concord-server: configurable key size for generated key pairs;
- concord-server: expose Jetty Sessions metrics;
- concord-task: new method `getOutVars` to retrieve out variables of
already running or finished processes;
- concord-task: support for `outVars` for `action: fork` when
`suspend` is enabled;
- concord-server: new `exclusive` syntax for triggers and regular
processes.

### Changed

- concord-server: optimize the agent command dispatching;
- concord-console: fixed overflowing in the Ansible event list popup;
- concord-server: fixed an issue with incorrect process status
transition of forked processes;
- concord-server: fixed an issue preventing the process wait
condition history from being correctly filled in.



## [1.32.0] - 2019-09-04

### Added

- concord-server: the process resume event name is now exposed as
`eventName` variable. It can be used to detect when the process is
restored from a checkpoint or resumed after suspension;
- concord-server: regexes are now supported in the process'
`requirements`;
- concord-tasks: `suspend` support for `action: fork`;
- concord-tasks: new method `suspendForCompletion` - suspends
the parent process until the specified processes are done;
- http-tasks: option to disable automatic redirect follow with
`followRedirects: false`;
- concord-server, project-model: initial version of the new
streamlined GitHub trigger implementation (opt-in).

### Changed

- concord-console: fixed a date-formatting bug preventing `date` and
`dateTime` process form fields from working correctly; 
- ansible: fixed an issue preventing the host status callback from
working correctly when the host is unreachable;
- ansible: callback plugins now send a custom `User-Agent` header;
- concord-server: optimize the process dispatching, perform filtering
on the server's side;
- concord-server: parallel processing of external event triggers; 
- concord-console: hide form actions on the process status page if
the process is stopped;
- concord-agent: move some of the system log messages to the `debug`
level;
- concord-server: additional logging when the process is enqueued.



## [1.31.1] - 2019-08-27

### Changed

- concord-console: new icon for the `NEW` process status;
- concord-console: fix the process status page refresh.



## [1.31.0] - 2019-08-27

### Added

- concord-runner: additional diagnostic when the process state
contains non serializable variables;
- concord-runner, concord-sdk: expose the `DependencyManager` to
plugins. Allows plugins to download external artifacts using a
persistent cache directory;
- concord-console: add pagination and server-side filtering to
the organization list page;
- concord-console: display trigger information on the process status
page.

### Changed

- concord-server: allow overriding of `requirements` with `profiles`;
- inventory: the `query` method now automatically retries in case of
network or intermittent backend errors; 
- concord-server: fixed an issue preventing processes that were
scheduled for future (using `startAt`) from resuming correctly;
- docker-tasks: now `stdout` output is captured without Docker system
messages (i.e. without the image download messages);
- concord-console: relax validation rules for `git+ssh` repository
URLs. Allows usage of non-GitHub GIT URLs;
- concord-server: fixed an issue preventing the "payload archive"
endpoint (the one that accepts ZIP archives as
`application/octet-stream`) from working.



## [1.30.0] - 2019-08-18

### Added

- concord-server: expose "the number of enqueued processes that must
be executed now" as a separate metric (i.e. without `startAt` or with
`startAt` in the past);
- concord-server: expose `repoBranch` and `repoPath` in
the `projectInfo` variable;
- concord-console: show the process' timeout of the status page;
- project-model: support for `retry` for flow `call` blocks.

### Changed

- http-tasks: skip certificate validation for all certificates (not only
for self-signed);
- concord-server: process start requests are now handled
asynchronously. The process queue entry is created as soon as
possible with the `NEW` status and processes in a separate thread
pool;
- ansible: fixed passing of `--ssh-extra-args` parameters;
- concord-agent, queue-client: replace `maxWebSocketInactivity`
parameter with `websocketPingInterval` and
`websocketMaxNoActivityPeriod`. Use the latter to detect dead
connections.



## [1.29.0] - 2019-08-06

### Added

- concord-server: process events now can be filtered by their
sequence ID in the `/api/v1/process/{id}/events` endpoint.

### Changed

- concord-server: allow project `OWNERS` to download state archives
of the project's processes;
- concord-server: record `github` events before starting any
processes;
- http-tasks: use `UTF-8` for string and JSON requests by default;
- concord-server, concord-ansible: use the event's timestamp instead
of the DB's timestamp when recording events (if available);
- concord-console: fix the project payload settings being applied
when the settings page opens;
- concord-console: use the same process toolbar on all tabs on the
process page.



## [1.28.1] - 2019-08-01

### Changed

- concord-server: use MDC to log process ID in the pipeline
processors;
- http-tasks: improved input parameter validation, additional validation
for JSON responses;
- concord-console, concord-runner: fixed displayed duration for
Ansible and process events;
- concord-server: allow retrieval of public keys of project-scoped
key pairs;
- ansible: improved calculation of host statuses during playbook
execution.

### Breaking

- concord-server: `sync=true` option is removed. Processes can no
longer be started in the "synchronous" mode, users should poll for
the process status updates instead.



## [1.28.0] - 2019-07-27

### Added

- concord-server: save external `github` events into the audit log;
- concord-server: save the process' trigger information in the
process queue. The process endpoints now return the new `triggeredBy`
field;
- concord-console: display the process' `startAt` on the process
status page;
- concord-server: support for multiple `user` entries in the form's
`runAs` block;
- sleep-tasks, concord-server: a way to suspend a process until the
specified date/time.

### Changed

- concord-server, concord-console: the `acceptsRawPayload` property
in projects is replaced with `rawPayloadMode`. The old property is
deprecated. New projects are created with `rawPayloadMode: DISABLED`
by default;
- concord-tasks: fixed an issue when a subprocess is started using an
API key specified by the user with `suspend: true`;
- ansible: the `limits` parameter now accepts list and array values;
- concord-server: some optimizations for the process event processing
(including Ansible). Reduces the contention on the process queue
table;
- ansible: fixed an issue with events not being sent to the server in
some cases;
- concord-server: make organization names optional when using secrets
in `imports`;
- concord-console: fixed an issue with the profile selection in
the manual trigger popup;
- concord-console: hide system files in the process attachments list;
- concord-server: fixed an issue preventing `imports` from working
correctly in `onFailure` and other handlers;
- concord-console: add process tags to the process status page;
- concord-server: process `tags` can now be specified using a
comma-separated startup argument, e.g. `curl -F tags=x,y,z`;
- concord-server: store repository info (commit ID, commit message,
etc) early in the pipeline to preserve the data in case or process
startup errors (e.g. bad syntax).



## [1.27.0] - 2019-07-20

### Added

- http-tasks: support for `application/x-www-form-urlencoded`
(`request: form` type);
- concord-server, concord-console: pagination for the secret list;
- concord-server: support for "exclusive" triggers and "exclusive"
execution groups;
- concord-console: a form to change the secret's project.

### Changed

- bpm: updated to `0.58.1`, resolves an issue with incorrect
`context#getCurrentFlowName()` value in some cases;
- project-model: fixed a bug preventing `withItems` and `retry` from
working correctly when used together;
- concord-server: process statup errors now correctly shown when
the "browser link" is used;
- concord-server, concord-console: fix the Ansible event status
calculation;
- ansible: fixed an issue with using arrays as the `tags` parameter
values.



## [1.26.0] - 2019-07-10

### Added

- concord-console: add the UI's version to the About page.

### Changed

- concord-console: fixed the issue with duplicate results in
the "find user" field;
- concord-server: fixed potential NPE when searching users in
AD/LDAP (e.g. by using the Console's "find user" field);
- concord-console: fixed the bug preventing the clear button on the
process list's filter popup from working.

### Breaking

- concord-server: the inventory subsystem now only accepts JSON
objects as top-level entries;
- concord-server: the "Landing Page" support is removed.



## [1.25.0] - 2019-07-02

### Added

- concord-server: support for triggers in `entity` policies.

### Changed

- concord-server: GitHub trigger's `useInitiator` is now correctly
runs the process using the commit user's security context. This fixes
the issue with child processes not inheriting the initiator;
- concord-server: fixed the handling of `queue.concurrent` policies:
enqueued processes now track each running process instead of a single
one;
- concord-console: fixed the issue when a repository refresh error
persists even after the refresh dialog is closed.



## [1.24.2] - 2019-06-27

### Changed

- concord-server: use GitHub event's `ldap_dn` to determine the
event's initiator.



## [1.24.1] - 2019-06-27

### Changed

- concord-server: fixed the login when format of usernames
provided by users didn't match the data returned by the AD/LDAP
server;
- concord-server: fixed the initial loading of default process
configuration.



## [1.24.0] - 2019-06-25

### Added

- concord-server: automatic reload of `defaultConfiguration` file
without restart;
- http-tasks: support for query parameters;
- concord-server: option to disable automatic user creation for
the LDAP realm;
- concord-console: display error details for `FAILED` processes;
- concord-server, concord-console: support for AD/LDAP domains,
custom username validators;
- concord-server: API endpoints for role and permission management;
- concord-console: add "copy to clipboard" buttons to entity IDs;
- concord-server, concord-console: initial implementation of "manual"
triggers;
- project-model: `error` blocks support for `script` steps.

### Changed

- http-tasks: make `method: GET` default;
- concord-server: drop the `process_checkpoint` to `process_queue`
FK, use a cleaning job instead. Enabled usage of partitioning for
the `process_checkpoints` table;
- docker-tasks: remove dependency on `io.takari.bpm/bpm-engine-api`;
- concord-runner, docker-images: use `file.encoding=UTF-8` by
default. Fixes the issue with Unicode passwords;
- concord-server: correctly pass the parent's `requirements` when
forking a process.



## [1.23.0] - 2019-06-17

### Added

- smtp: support for attachments.

### Changed

- concord-server: fix symlink handling when importing the process
state;
- concord-tasks: fix the kill action. Now it is correctly accepts
the `instanceId` parameter.



## [1.22.0] - 2019-06-16

### Added

- concord-cli: initial release;
- project-model: support for configurable resource paths such as
`./profiles`, `./flows`, etc;
- project-model: new trigger type - `manual`. Can be used to
configure process entry point available through the UI;
- ansible: initial support for installing additional pip packages
using Python's virtualenv;
- ansible: support for multiple vault IDs/passwords;
- concord-runner: configurable time interval without a heartbeat
before the process fails;
- concord-runner: the current flow name is now available via
`${context.getCurrentFlowName()}` method;
- concord-server: return a list of form `fields` in the generated
`data.js` for custom forms. The list is in the original order of the
form definition.

### Changed

- concord-server: fixed a potential NPE when handling process
metadata;
- ansible: keep both the head and the tail when trimming long string
values in events;
- project-model: `withItems` now supports iteration over Java Map
elements;
- docker-tasks: make `cmd` optional;
- concord-server, concord-agents: external `imports` are now
processed without copying into the process state;
- ansible: send process events asynchronously;
- concord-client, concord-runner: use a custom `User-Agent` header;
- concord-server: fixed a NPE when handling optional `file` form
fields.

### Breaking

- project-model: external `imports` are now a top-level element.



## [1.21.0] - 2019-05-23

### Added

- concord-server: additional metrics for the process key cache;
- concord-server: configurable delay when polling for agent commands;
- concord-console: show the "so far" duration of active statuses on
the process status history page;
- concord-runner: new options to enable or disable recording of task
`in` and `out` variables. Including the option to blacklist specific
variable names;
- concord-console: display task call details in the process log
viewer;
- project-model: support for expressions in form calls.

## Changed

- docker: the task can now be called using the regular `task` syntax
(in addition to the previous `docker` form);
- concord-server: keep the original values of `readonly` form fields
on submit;
- concord-server: throttle the AD/LDAP user group caching;
- concord-console: fixed the log toolbar's flickering issue.



## [1.20.1] - 2019-05-16

### Changed

- concord-server: fix the `imports` processing - `configuration`
objects from the imported resources are now loaded correctly;
- concord-server: another fix for the issue with symlinks in GIT
repositories.



## [1.20.0] - 2019-05-16

### Added

- http-tasks: new `debug` parameter. Enables additional logging of
request and response data;
- concord-tasks: log the job's URLs when starting new processes;
- concord-console: the process log viewer's options are now persisted
using the browser's Local Storage.

### Changed

- project-model: fixed an issue with nested objects used in
`withItems`;
- concord-server: fixed an issue with circular symlinks in GIT
repositories;
- concord-console: fixed an issue preventing the UI from working
correctly in MS Edge (missing `URLSearchParams` polyfill);
- concord-agent: ignore subsequent attempts to enable the maintenance
mode;
- concord-server: `SUSPENDED` processes are now ignored when
calculating the concurrent processes limit;
- concord-console: fixed an issue preventing checkpoints from being
rendered correctly in some cases.



## [1.19.1] - 2019-05-12

### Changed

- concord-console: better handling of log tag parsing errors;
- concord-runner, concord-console: use a less common log tag name;
- k8s/agent-operator: simultaneously handle pool size and
configuration changes.



## [1.19.0] - 2019-05-12

### Added

- concord-console: option to split process logs by task calls;
- concord-tasks: support for file attachments when starting new
processes;
- concord-sdk, crypto-tasks: expose `encryptString` method to flows;
- slack: support for creating and replying to threads;
- concord-agent, concord-runner: support for a configurable list of
volumes to mount into Docker containers created by plugins.
- concord-tasks: support for file attachments when starting a process

### Changed

- concord-tasks: improved validation of input parameters;
- ansible: fixed handling of `auth` parameters. The deprecated `user`
and `privateKey` parameters are working now again;
- concord-console: the URL parsing in the log viewer is updated to
better handle URLs in quotes.



## [1.18.1] - 2019-05-07

### Changed

- concord-server: fixed the handling of processes with wait
conditions.



## [1.18.0] - 2019-05-05

### Added

- ansible: support for multiple `inventory` and `inventoryFile`
entries;
- log-tasks: `logDebug`, `logWarn` and `logError` tasks;
- concord-server: initial support for RBAC permissions;
- ansible: initial Kerberos authentication support.

### Changed

- project: initial support for server-side plugins. Ansible-related
endpoints are moved into `server/plugins/ansible`;
- concord-console: stop the form wizard when the user navigates away;
- docker: include the Taurus PIP module in the default Ansible
image;
- concord-server: improve error messages in case of authentication
failure due to an invalid input or an internal error.



## [1.17.0] - 2019-04-24

### Added

- docker: install Ansible's `k8s` dependencies;
- k8s/agent-operator: initial version;
- concord-server: return `requirements` when fetching a list of
processes using `/api/v2/process`;
- concord-server: SSO support for custom forms;
- concord-console: make the log viewer URLs clicable;
- ansible: support for non-root paths when fetching external roles;
- http-tasks: new parameter `requestTimeout`.

### Changed

- http-tasks: make `response` parameter optional;
- concord-server: fixed the SSO https->http redirect;
- concord-console: performance optimizations for the log viewer;
- concord-runner: allow 2 letter checkpoint names.



## [1.16.1] - 2019-04-18

### Changed

- concord-console: use the configured project metadata to render the
checkpoints page;
- concord-runner: allow whitespaces in checkpoint names.



## [1.16.0] - 2019-04-17

### Added

- concord-server: new configuration parameter
`ldap.excludeAttributes` - provides a way to exclude specific
LDAP attributes from being returned in the user's `attributes`;
- concord-server, concord-console: JWT-based SSO service support;
- ansible: existing JSON and YAML extra vars files can now be used
with the new `extraVarsFiles` parameter;
- resource-tasks: new method `prettyPrintJson` - returns formatted
JSON as a string;
- concord-server, concord-console: ability to disable a process to
prevent restoring it from a checkpoint after completion.

### Changed

- concord-server: filter out all non string LDAP attributes;
- concord-server: support for multivalue LDAP attributes when
fetching user details from AD/LDAP;
- concord-console: fixed the page limit dropdown on the checkpoint
view page;
- concord-common: do not escape backslashes when creating a ZIP
achive;
- project-model, concord-runner: support expressions in checkpoint
names.



## [1.15.0] - 2019-04-03

### Added

- concord-agent, concord-runner: support for the configurable
`logLevel`.

### Changed

- concord-server: fix the `${initiator}` and `${currentUser}` data
fetching when dealing with multiple account types.



## [1.14.0] - 2019-03-31

### Added

- policy-engine, concord-server: support for entity owner-based
policies;
- concord-tasks: new action `startExternal`. Can be used to start a
new process on an external Concord instance.

### Changed

- concord-client, concord-tasks: fixed a bug preventing `baseUrl`
and `apiKey` parameters from being correctly applied;
- http-tasks: correctly handle empty (204) responses;
- concord-server: fixed a potential NPE when setting a new owner for
projects without owner.



## [1.13.1] - 2019-03-27

### Changed

- http-task: make `ignoreErrors` work with connection timeouts;
- concord-server: fixed an authentication issue with passwords that
end with a `:`;



## [1.13.0] - 2019-03-26

### Added

- concord-sdk: additional `MapUtils` methods to retrieve Map, List
and Integer values;
- concord-console: show user display names on the team member list
page;
- concord-server: new user attribute - `displayName`. Automatically
stored for AD/LDAP users. For local users it can be set using
the API;
- concord-server, concord-console: project owners can now be updated
using the API or the Console;
- concord-server, consord-console: organization owners can now be
set using the API or the Console;
- concord-server, concord-runner, concord-tasks: (optionally) suspend
the parent process while waiting for a child process (only for
`start`);
- project-model: support for arrays in `withItems`;
- concord-server: an API method to remove an organization
(admin only).

### Changed

- concord-agent: fix JVM "pre-forking". Now the process poll is
correctly shared between all workers;
- concord-server: more detailed error messages in case of invalid
encrypted strings;
- concord-console: fixed a bug when team members could not be
deleted.



## [1.12.0] - 2019-03-13

### Added

- policy-engine, concord-server: support for organization-level max
concurrent process limits;
- concord-console: process metadata filtering for the checkpoint list
page;
- concord-server, concord-console: teams can now be associated with
LDAP groups;
- concord-server: user accounts can now be disabled via an API call;
- concord-server: new automatically-provided process
variable - `requestInfo`. Contains the request's parameters, headers
and the user's IP address.

### Changed

- concord-agent: fixed the issue preventing the process startup
errors from being logged correctly;
- concord-console: fixed a login issue with non-ASCII usernames or
passwords.



## [1.11.0] - 2019-03-04

### Added

- concord-server, ansible: track `retry` count per host;
- concord-agent: failover support for the websocket connections; 
- ansible: print a "No changes will be made" warning if `check` or
`syntaxCheck` modes are used.

### Changed

- concord-console: fixed the handling of processes with checkpoints
but without history;
- concord-server: when a repository refresh fails show the error
cause instead of a wrapped exception;
- project-model: fixed the behaviour of nested and/or sequential task
calls with `retry`;
- ansible: the task now correcly records both pre- and post-action
events;
- concord-console: fixed the log timestamp pattern, now Ansible log
timestamps are correctly converted into the local time;
- ansible: `{%raw%}` strings are working again.



## [1.10.0] - 2019-02-27

### Added

- concord-server: support for GitHub webhooks limited to a specific
repository;
- forms: support for `date` and `dateTime` field types;
- concord-server, concord-console: ability to temporarily disable
repositories.

### Changed

- concord-server: return `400 Bad Request` when trying to
`decryptString` in a process without the project;
- bpm: updated to `0.54.0` to support expressions in `script` names.



## [1.9.0] - 2019-02-20

### Added

- concord-server: option to sign the `initiator` and `currentUser`
usernames. Signatures can be validated using the configured public
key.

### Changed

- concord-agent: when resolving dependencies, use `latest` as the
indicator of an automatically selected version;
- concord-server: remove keywhiz support. The Concord-Keywhiz
integration could be implemented as a separate plugin;
- concord-server: removed support for archiving process state and
checkpoints into an S3 endpoint. The recommended way is to use table
partitioning or a custom (external) data retention solution;
- concord-console: fixed the process list filtering.



## [1.8.1] - 2019-02-18

### Changed

- concord-agent: fix the dependency resolution for plugins with
non-default versions;



## [1.8.0] - 2019-02-17

### Added

- concord-server: option to retrieve a single item in an inventory;
- concord-sdk: initial implementation of `LockService`; 
- concord-server: support for `activeProfiles` in `cron` triggers;
- concord-server: new endpoint `GET /api/v2/process`. Returns a list
of processes with optional filtering (including metadata) and
pagination;
- concord-sdk: new utility methods to work with the process
variables;
- concord-server: initial support for in-process locks.

### Changed

- concord-server: force logout users on any authentication error.
Fixes the issue with "remember me" users with passwords changed
between the server's restarts;
- repository: clean up and reset locally cached repositories on
checkout;
- concord-server: skip invalid host names when processing Ansible
events;
- concord-runner: more graceful handling of errors while saving "out"
variables;
- concord-server: check for permissions when retrieving process
details via `GET /api/v2/process/{id}`.
- concord-server, concord-agent: load the dependency version list
from the server.



## [1.7.2] - 2019-02-11

### Changed

- concord-server: forks should keep the original `_main.json` minus
the `arguments`. This fixes the issue with forks and
`onCancel/onFailure` handlers which are using external dependencies;
- concord-console: fixed the row selection bug in the process list
component.



## [1.7.1] - 2019-02-09

### Changed

- concord-server: fix authorization of `cron` triggers.



## [1.7.0] - 2019-02-07

### Added

- throw-tasks: now capable of throwing exceptions with custom
payload: maps, lists, etc;
- concord-console: new "Wait Conditions" tab on the process status
page;
- concord-server: new process configuration option `exclusiveExec`:
restricts the process execution to one process at the time per
project;
- http-tasks: proxy support via `proxy` parameter;
- concord-server: option to restrict the external events endpoint
`/api/v1/events/{eventName}` to users with specific roles.

### Changed

- ansible: Concord polices now receive interpolated variable values;
- concord-console: display empty checkpoint group for suspended
processes;
- concord-runner: save and restore the last known variables for
forked processes. This allows forks and onCancel/onError/etc
handlers to access the parent process' variables;
- concord-server: use roles instead of user flags. E.g.
`concordAdmin` role instead of `USERS.IS_ADMIN`.



## [1.6.0] - 2019-01-31

### Added

- concord-console: new flow selection dropdown in the process start
popup.

### Changed

- concord-server: do not copy the parent process' forms when forking
a process;
- repository: fix the GIT clone bug for repositories without a
`master` branch;
- concord-console: fix the checkpoint grouping issue, preventing
checkpoints from being correctly rendered;
- project-model: improve stacktraces in case of YAML parsing errors;
- concord-runner: fix the timestamp format in the `processLog`
logger. Plugins such as Ansible should use the correct timestamp
format now.



## [1.5.0] - 2019-01-30

### Added

- concord-server: new form attribute `submittedBy` - automatically
created for each form after it submitted, contains the form user
information. Can be enabled with `saveSubmittedBy` form call option;
- concord-console: add option to convert log timestamps into local
time;
- concord-server, concord-runner: use UTC in log timestamps;
- concord-agent: additional logging while downloading the process'
repository data and state;
- concord-server, concord-console: "Remember Me" cookie support;
- concord-console: list of checkpoints on the process status page;
- concord-runner: new method `context#form`, allows dynamic creation
of forms in tasks, scripts and expressions;
- concord-console: profile selection when starting a process;
- concord-tasks: new task `concordSecrets`.

### Changed

- concord-server, concord-agent: disable `git.httpLowSpeedLimit` as
it was causing major performance issues when cloning large
repositories;
- concord-server: reduce the default max session age to 30 minutes.



## [1.4.0] - 2019-01-23

### Added

- concord-server: return the build's commit id in the server version
response;
- vars plugin: `get` method can now return nested variables or
fallback to a specified value;
- concord-server: new endpoint `/api/v2/process/{id}`, allows
customization of the data included in the response;
- concord-server: new API endpoint `/api/v2/process/{id}/checkpoint`,
allows restoring checkpoints with a `GET` request;
- concord-console: bring back the status column to the child process
list;

### Changed

- concord-server, project-model: allow expressions in the form's `runAs`
parameter;
- concord-server: close websocket channels when the maintenance mode
is enabled;
- ansible: fixed an issue that caused the sensitive data masking
plugin (`concord_protectdata.py`) to fail on non-ASCII strings.



## [1.3.0] - 2019-01-15

### Added

- concord-console: list of attachments added to the process status
page;
- slack: when sending a message, the task now returns a `result`
object.

### Changed

- concord-console: flow events moved to a separate tab on the process
status page;
- concord-server: copy the parent process' repository info to forked
processes. This fixes an issue preventing process forks from working
correctly.



## [1.2.2] - 2019-01-14

### Changed

- concord-server: store custom form files in the process state
regardless of whether they are form a repository or not. This fixes
an issue preventing custom forms from working correctly.



## [1.2.1] - 2019-01-13

### Changed

- concord-agent, concord-runner: log additional performance metrics
when running in `debug` mode;
- dependency-manager: pre-sort dependency URIs to ensure stable
dependency resolution order. This improves chances of getting a
pre-forked JVM instead of spinning a new one;
- concord-console: fix potential data race when loading process
checkpoints;
- concord-server: last modified date of process state files is now
correctly calculated when importing the state.



## [1.2.0] - 2019-01-09

### Added

- http-tasks: support for `PATCH` method.

### Changed

- concord-server: fixed a bug preventing the Ansible events from
being processed correctly;
- concord-console: fixed the parsing of GIT URLs on the repository
list page;
- concord-server: fixed an issue preventing git submodules using the
default (token-based) auth from working;
- concord-server: reject flow attachments if "Allow payload archives"
is disabled in the project's settings;
- concord-console: disable "New Project" button if the user is not a
member of the organization.



## [1.1.0] - 2019-01-04

### Added

- concord-console: new "Checkpoint View" for projects;
- concord-console: allow addition of new elements for `type*` and
`type+` form fields.

### Changed

- concord-console: the process start popup now correctly displays the
branch of the selected repository;
- concord-server: allow project-scoped secrets to be used when
cloning the project's repositories;
- concord-agent: improved error logging when cloning repositories;
- concord-runner: fixed an issue preventing the runner from
terminating correctly on `java.lang.Error`;
- concord-server: set the default session timeout to 10 hrs (was
unlimited);
- concord-server: accept GitHub events without branch information
(e.g. archive events).



## [1.0.0] - 2018-12-26

### Added

- concord-server, concord-console: custom process list filters based
on process metadata;
- concord-server: new `/api/v2/trigger` endpoint. Currently only the
list method is provided;
- concord-console: externalize extra links in the system menu, allow
for environment-specific overrides;
- concord-console: ability to specify the entry point when starting
a process;
- ansible: new `syntaxCheck` option to run
`ansible-playbook --syntax-check`;
- ansible: new `check` option to run `ansible-playbook --check`.

### Changed

- concord-console: fixed an issue with error messages persisting
after navigating out of the page;
- concord-server: fixed the logic of the Ansible event processor. Now
it should correctly handle very long-running processes;
- concord-console: fixed a bug preventing the Ansible host filter
from working correctly;
- concord-runner: fix the handling of process arguments when
restoring a process from a checkpoint;
- concord-server, concord-agent: perform `git clone` on the Agent,
keep only the changes in the DB;
- bpm: updated to 0.51.0, fixed the resolution order of tasks and
variables. Now flow variables can shadow the registered tasks;
- concord-console: prevent table overflow on process detail table;
- concord-console: move the Ansible stats table above the flow event
table;
- concord-server: fixed the host status calculation in the Ansible
event processor. Now `SKIPPED` is correctly overwritten by other
statuses in multi-step plays.



## [0.99.1] - 2018-12-06

### Changed

- ansible: use `/tmp/${USER}/ansible` as the default `remote_tmp`;
- concord-server: fixed a bug in `/api/service/process_portal`
preventing the endpoint from working.



## [0.99.0] - 2018-12-05

### Added

- project-model: support for programmatically-defined form fields;
- concord-server: new `useEventCommitId` parameter of `github`
triggers;
- concord-server: `repoBranchOrTag` and `repoCommitId` parameters to
start a process with the override of the configured
branch/tag/commitId values;
- concord-console: pagination for the Ansible host list on the
process status page;
- http-tasks: new parameter `ignoreErrors` - instead of throwing exceptions
on unauthorized requests, the task returns the result object with the
error;
- slack: new `slackChannel` task for creating and archiving channels
and groups;
- concord-server: a metric gauge for the number of currently
connected websocket clients;
- misc-tasks: new `datetime` task;
- concord-server: pagination support for the child process list page;
- concord-server: support for policy inheritance;
- concord-server: `offset` and `limit` to the process checkpoint list
endpoint;
- concord-server: support for exposing form and nested values as
process metadata;
- concord-server: support for default metadata values.

### Changed

- docker: set the minimal Ansible version to 2.6.10;
- concord-project-model: forbid empty flow and form definitions;
- concord-server: use a single local clone per GIT URL;
- concord-server: fixed an issue, causing `onFailure` to fire up
multiple times in clustered environments.
- concord-server: `cron` triggers are now using the DB's time to
calculate the schedule;
- concord-console: improved repository validation error messages;
- concord-console: dropdown menus with optional values now correctly
render the empty "value";
- concord-console: fixed a bug preventing the child process list from
working correctly;
- concord-server, concord-console: Ansible events are now
pre-processed and stored on the backend, making the Process Status
page more responsive when working with large Ansible plays.
- concord-server: evaluate parsed expression value in custom form field's
`allow` attribute
- concord-server: change the (potential) partitioning key in
`PROCESS_EVENTS` from `EVENT_DATE` to `INSTANCE_CREATED_AT`.



## [0.98.1] - 2018-11-23

### Changed

- concord-queue-client: fixed potential OOM when handling connection
errors.



## [0.98.0] - 2018-11-18

### Added

- concord-server: add `meta` to the process checkpoint list endpoint.
- concord-console: pagination support for the process list.

### Changed

- concord-console: fixed a session handling bug. Now the session is
correctly restored on UI reload. 


## [0.97.0] - 2018-11-13

### Added

- http-task: `connectTimeout` and `socketTimeout` parameters;
- concord-server: GitHub triggers can now use `payload` field with
the original event's data;
- concord-server: new API endpoint to retrieve a list of processes
including their status history and checkpoints;
- concord-console: a warning if a password stored as a secret is too
weak;
- concord-agent: an endpoint to get the current status of the
maintenance mode.

### Changed

- concord-server: removed GitHub webhook registration and repository
cache;
- concord-server: fixed a bug preventing relative file upload paths
from working with `/api/v1/process` endpoint;
- concord-server: the session cookie (`JSESSIONID`) is now marked as
`HttpOnly`;
- concord-common: ensure that `CONCORD_TMP_DIR` environment variable
is defined;
- concord-server: fixed a bug causing incorrect matching of
Concord repositories with incoming GitHub events.



## [0.96.0] - 2018-11-07

### Added

- concord-console: new tab on the process status page - "Child
Processes";
- project-model, concord-server: support for `readonly`,
`placeholder` and `search` form field attributes;
- project-model: `withItems` now correctly handles `out` variables of
tasks;
- concord-server: support for GitHub events other than `push` or
PR-related events; 
- concord-server: GitHub webhook support for unknown (not registered
in Concord) repositories.

### Changed

- concord-server: fixed a bug preventing process checkpoints from
being correctly archived;
- docker: updated Ansible to 2.6.7.

### Breaking

- concord-tasks: IN parameter `jobs` renamed to `forks` to avoid
collision with OUT parameter `jobs`.



## [0.95.0] - 2018-11-03

### Added

- concord-server, concord-runner: store `lastError` in `out`
variables in the process' metadata.
- concord-server: `afterCreatedAt` parameter to the process list API
endpoint.

### Changed

- concord-server: allow admins to access any form;
- project-model: checkpoint names must be unique across all loaded
flow definitions;
- project-model: fixed a bug preventing nested `withItems` from
working correctly;
- bpm: updated to 0.49.0. Now all context types implement `eval` and
`interpolate` methods.

### Breaking

- concord-client: `ProcessApi#metadata` renamed to `#updateMetadata`.



## [0.94.0] - 2018-10-29

### Added

- concord-server: optional "default filter" for all GitHub triggers;
- concord-server: make optional the unknown GitHub webhook removal.

### Changed

- concord-runner: fixed a bug preventing dynamic task registration
from working correctly.



## [0.93.1] - 2018-10-28

### Changed

- concord-server: fixed a bug preventing the checkpoint archiver
from working correctly.



## [0.93.0] - 2018-10-28

### Added

- concord-server: add `payload` to `github` trigger events; 
- concord-server: GitHub webhook URLs can now supply additional
parameters via query parameters;
- concord-server: configurable period values for cleanup tasks;
- concord-sdk: support for "protected" variables that can be set only
by allowed tasks;
- ansible, policy-engine: support for restricting of allowed URLs in
`maven_artifact`, `uri` and `docker_container`;
- policy-engine: support for value expressions;
- concord-console: new process history tab on the status page.

### Changed

- concord-server: allow GitHub events without explicit webhook
registration (e.g. organization-level hooks);
- concord-console: the process filtering list is performed on the
server now;
- concord-server: `task_locks` are replaced with the task schedule
table;
- concord-server: merge the existing process variables with template
variables;
- bpm: updated to 0.48.0, fixed `context#getVariableNames` issue.

### Breaking

- concord-server: `github.enabled` configuration parameters renamed
to `github.webhookRegistrationEnabled`;



## [0.92.0] - 2018-10-21

### Added

- concord-server: optional rate limit for process start requests;
- concord-server: ability to assign a policy to a user;
- ansible: additional validation for `groupVars`.

### Changed

- concord-server: return `429` then requests are rate-limited or
rejected by the queue policy;
- concord-server: fixed an issue preventing organization data from
being correctly updated via REST;
- project-model: using `withItems` with `null` now skips the block's
execution instead of throwing an error.  



## [0.91.0] - 2018-10-14

### Added

- concord-server: support for `timezone` in `cron` triggers;
- concord-console: ability to cancel multiple processes;
- concord-server: the secret decryption error now contains the
secret's name;
- concord-server, concord-console: refresh GitHub webhooks when a
repository is refreshed;
- concord-server: timeout options for GIT's HTTPS and SSH transports; 
- concord-server: a policy rule for setting the maximum allowed
`processTimeout` value.

### Changed

- concord-server: the "default variables" file replaced with the
"default configuration" file. Instead of containing the `arguments`
section, it now contains the while `configuration` object.

### Breaking

- concord-server: `process.defaultVariables` configuration file
parameter renamed to `process.defaultConfiguration`;
- concord-sdk: `Constants.Request.USE_INITIATOR` renamed to
`Constants.Trigger.USE_INITIATOR`.



## [0.90.0] - 2018-10-09

### Added

- concord-server: assign an save a unique "request ID" to link audit
logs and the process queue data; 
- concord-server: ability to restrict the max number of forks using
policies;
- concord-server: ability to restrict the max number of processes in
the queue for a given status using policies;
- docker: `envFile` parameter to define environment variables using a
file;
- concord-console: show repository names in the project process list;
- concord-server: ability to overwrite process configuration using
policies.

### Changed

- concord-server: fixed an issue preventing process timeouts from
correctly working with multiple running processes; 
- concord-tasks: override the API endpoint with `baseUrl`. 



## [0.89.3] - 2018-10-02

### Changed

- concord-console: fixed an issue preventing the process start
redirect from working.



## [0.89.2] - 2018-10-01

### Changed

- concord-server: fixed variabled of the `spec` field in `cron`
triggers; 
- concord-server: fixed an issue causing `cron` triggers to use the
default flow.



## [0.89.1] - 2018-09-30

### Changed

- ansible: fix a bug preventing the task callback from recording task
start events.



## [0.89.0] - 2018-09-30

### Added

- concord-server: user-defined process timeouts support;
- concord-server: "initiator passthrough" support for OneOps and
GitHub events;
- concord-server: added support for GitHub PR events;
- concord-agent, concord-runner: initial support for "container per
process" execution model.

### Changed

- ansible: use ANSI colors by default;
- concord-server: fixed a bug preventing checkpoints from working
with `.concord.yml` files.



## [0.88.0] - 2018-09-19

### Added

- dependency-manager: load plugin versions from a file. Allows
omitting the version qualifier for the registered plugins.

### Changed

- concord-server: fixed a bug preventing repositories from being
automatically refreshed on GitHub push events;
- concord-console: fixed project process list filtering.



## [0.87.0] - 2018-09-16

### Added

- concord-console: prevent loading of too much data on the process
status page, show a warning instead;
- concord-server: support for email form fields
(`inputType: "email"`);
- concord-server: expose Jetty statistics;
- concord-console: add filtering to the organization list;
- concord-console: UI for managing access to projects and secrets;
- concord-server, concord-console: initial support for process-,
organization- and project-level metadata.

### Changed

- concord-server: fixed a bug preventing GIT repositories with large
number of tags from working;
- concord-server: apply RBAC filters when listing secrets.



## [0.86.0] - 2018-09-05

### Added

- concord-server: new API method to list process checkpoints;
- concord-agent: option to ignore SSL certificate errors for API
calls;
- concord-console: add filtering to the secret list and the team list
pages;
- concord-server, concord-console: option to use a service account to
retrieve GIT repositories instead of user keys;
- concord-server: store project keys (used in `encrypt/decryptString`
methods) in the DB. 

### Changed

- concord-console: fix handling of process statup errors;  
- concord-server: in LDAP auth try `userPrincipalName` first;
- concord-server: require `READER` access level to refresh triggers
(instead of `WRITER`);
- concord-server: process-level variables are now correctly override
the system-wide defaults;
- concord-server: fixed audit logging for AD/LDAP authentication;
- concord-server: improved audit logging for projects and
repositories;
- concord-server: store `initiatorId` instead of `initiator` username
in the process queue table;
- project-model: fix handling of `null` values in `set` step.



## [0.85.0] - 2018-08-15

### Added

- concord-server: additional metrics, process queue gauges;
- resource-tasks: new method to read YAML files. Optional support for
string interpolation in JSON and YAML files;
- ansible: initial support for external roles.

### Changed

- runner: upgrade the BPM engine version to `0.47.1` to fix a bug
preventing correct handling of IN variable evaluation errors.



## [0.84.1] - 2018-08-12

### Changed

- concord-server: fix missing `@Named` annotations for JOOQ
configuration. Causes the wrong datasource to be injected.



## [0.84.0] - 2018-08-12

### Added

- concord-server: `${processInfo.activeProfiles}` variable;
- concord-runner: new utility task `forms` to create links to process
forms and the form wizard;
- concord-server, concord-agent, project-model, concord-runner:
initial support for process checkpoints;
- concord-server, policy-engine: support for "max concurrent
processes" policy.

### Changed

- docker: increased the default API proxy timeout to 180 seconds;
- concord-console: fix the custom view link on the process form page;
- concord-console: fixed retieval of "Last 10 processes" on the
Activity page;
- concord-server: fixed a bug causing `onFailure` handler processes
to fail due to missing session keys and `projectInfo` variables.

### Breaking

- concord-server: form API endpoints now accept form names instead of
IDs. E.g. `/api/v1/process/PROCESS_ID/form/FORM_NAME`.



## [0.83.1] - 2018-08-06

### Changed

- concord-server: revert inventory RBAC changes, allow publicly
writable inventories;
- concord-console: fix the process status filter on the project
processes page;
- concord-console: limit `Activity` data to the current user's
organizations.



## [0.83.0] - 2018-08-05

### Added

- concord-server: more metrics;
- concord-console: new process list filters - `Status` and
`Initiator`;
- concord-console: new default page `Activity`;
- concord-console: allow updating of project visibility and
description;
- concord-server: improved support for multi-select fields in custom
forms;
- ansible: option to save Ansible host statistics as a flow variable;
- http-tasks: support for `DELETE` method;
- project-model: support for `activeProfiles` in trigger definitions;
- concord-server: option to disable triggers (specific or all);
- concord-server: the Prometheus metrics endpoint now proxied by the
console's nginx.

### Changed

- concord-server: fix permissions check when killing a process;
- concord-console: fix duplicate form events;
- concord-console: replace automatic redirect to a custom form with a
link;
- concord-console: escape HTML in process logs.



## [0.82.0] - 2018-07-29

### Added

- concord-server: additional metrics (JVM, memory, JDBC, etc).



## [0.81.0] - 2018-07-24

### Added

- concord-server: delay before processes are moved into the archive; 
- concord-server: support for custom form validation messages;
- concord-server: option to disable GIT's "shallow clone";
- concord-server: initial integration with Prometheus;
- concord-server: option to disable the process state archiving task.

### Changed

- concord-server: make the process state archiving task to pick up
next items as soon it's done with the current work.



## [0.80.0] - 2018-07-22

### Added

- project-model: support for multiple Concord project files in
`concord/*.yml`;
- concord-server: support for archiving of process state into
S3-compatible object stores;
- concord-server: optionally encrypt sensitive data in the process
state;
- project-model: allow expressions to be used in flow calls;
- plugins: new built-in task `throw`;
- concord-console: ability to filter Ansible hosts by their inventory
group;
- concord-server: RBAC checks for process kill and status update
operations.

### Changed

- concord-server: return `404` when the secret doesn't exist;
- concord-server: fix handling of empty inventory query params.
- concord-server: fixed the repository webhook registration when
project or repository is created or updated.



## [0.79.2] - 2018-07-16

### Changed

- concord-agent: fixed the configuration variable names;
- concord-agent: increase default timeout values;
- concord-server: correctly handle table aliases in inventory
queries;
- concord-server: escape special characters in commit messages
`processInfo.repoCommitMessage`.



## [0.79.1] - 2018-07-16

### Changed

- concord-server: fixed an issue preventing
`initiator.attributes.mail` from populating.



## [0.79.0] - 2018-07-15

### Added

- project-model: additional YAML validations to prevent duplicate
keys such as `configuration` or `arguments`;
- concord-server: an option to perform DB migrations separately;
- concord-server: periodic audit log clean up;
- concord-server: option to disable audit logging;
- concord-server: added limits for "encrypted strings" size;
- concord-server: improved error reporting for non-process related
errors;
- concord-server, concord-console: repository metadata to processes;
- concord-server: improved inventory query validation;
- concord-console: display flow and ansible event duration;
- ansible: record "pre" and "post" task events separately;
- concord-console: new API key management UI;
- concord-server: optional API key expiration;
- concord-server: methods to list existing inventories and inventory
queries;
- concord-server, concord-console: new method to validate a repo
without starting it;
- concord-console: new form to encrypt values to use with
`crypto.decryptString`;
- concord-server: all secured endpoints will now return appropriate
auth challenge headers;
- concord-console: scroll back to top button on the process log page;
- concord-console: highlighting of errors and warning messages in
process logs;

### Changed

- docker-images: increased nginx's default max request size to 32Mb;
- concord-server: a single invalid trigger no longer prevents other
triggers from firing;
- concord-agent: now uses the same REST API endpoint to download
process state snapshots as regular users. 



## [0.78.1] - 2018-07-08

### Changed

- concord-server: fixed a bug that prevented the custom forms API
endpoint from registering correctly.



## [0.78.0] - 2018-07-08

### Added

- concord-project-model: improved validation of `withItems` values;
- concord-console: autoscrolling for process logs;
- concord-console: ability to rename secrets and update their
visibility;
- concord-console: the list of registered triggers for a repository.

# Changed

- concord-server: configuration migrated into a single configuration
file.
- concord-runner: full stacktraces will be printed out for unhandled
exceptions.



## [0.77.0] - 2018-07-01

### Added

- concord-server: project-scoped secrets;
- http-tasks: support for custom headers.

### Changed

- concord-client: better handling of server-side validation errors;
- concord-server: merge `api` and `impl` modules;
- concord-server: GitHub triggers will now correctly use the repo's
organization and project names to match the events;
- concord-server: now process state snapshots can only be accessed by
admins, process owners and "global readers".

### Breaking

- concord-server: removed the old project API endpoint
`/api/v1/project`.



## [0.76.0] - 2018-06-24

### Added

- concord-server: multiple ldap groups support in forms

### Changed

- concord-server: fixed the repository webhook registration when an
individual repository is created or updated;
- concord-console: project, team members and ansible host filters are
now using simple substring search instead of regular expressions;
- ansible: fixed a bug than prevented `outVars` from working for any
variables other than the first in the list.



## [0.75.0] - 2018-06-17

### Added

- policy-engine: optional rule violation messages;
- concord-console: the repository start popup now has a link to the
process log if the process failed to start;
- concord-console: the profile page and the API key form;
- ansible: new task parameter `outVars` to save specific Ansible
variables as Concord flow variables;
- concord-agent: a local interface to enable maintenance mode;
- concord-server: check template output for cycles to avoid
serialization issues;
- concord-server: size limit for binary data secrets.

### Changed

- concord-console: clean up the error message when users try to
access a form without a matching LDAP group;
- policy-engine: fix calculation of workspace sizes; 
- concord-console: fixed the server error details parsing;
- concord-rpc: module removed. 



## [0.74.0] - 2018-06-10

### Added

- concord-server: initial support for roles;
- concord-console: a field to filter the project list by name;
- concord-project-model: support for `withItems`. Allows iterating
over a list of elements;
- concord-server: support for `Bearer <token>` authorization;
- concord-console: the process page's organization and project links;
- concord-server: optional git repository check for `concord.yml`
being present.



## [0.73.0] - 2018-06-04

### Added

- concord-server: new `update` method for secrets. It allows changing
of secret names and/or visibility;
- concord-agent: retries for errors during download of process
payloads;
- concord-agent: `AGENT_ID` and `USER_AGENT` parameters to support
persistent agent IDs.

### Changed

- concord-server: fixed the secret access level endpoint not
accepting team names;
- concord-server: fixed "test repository connection" method.



## [0.72.0] - 2018-06-03

### Added

- concord-console: add `System` menu with `Documentation` and `About`
links;
- concord-agent: new `MAX_PREFORK_COUNT` configuration parameter to
limit the number of processes in the pool;
- ansible: new parameter `disableConcordCallbacks` to disable
Concord-specific Ansible callbacks: stdout filtering, event
recording, etc;
- ansible, concord-console: handle Ansible's `ignore_errors` modifier;
- ansible: ability to use password-less secrets in the secret
lookup plugin;
- concord-client, concord-agent, concord-runner: enable session
cookies;
- concord-task: fail the parent process if the subprocess has failed.
Added new parameter `ignoreFailures: true` to revert the previous
behavior.

### Changed

- concord-project-model: external form definitions must work with and
without a whitespace in the definition.



## [0.71.3] - 2018-05-31

### Changed

- concord-agent: fixed orphaned docker sweeper bug which caused live
containers to be terminated. 



## [0.71.2] - 2018-05-30

### Changed

- guava downgraded from 21.0 to 20.0 to avoid classpath issues with
some of the plugins (e.g. jira).

 

## [0.71.1] - 2018-05-29

### Changed

- concord-server: fix form wizard redirect;
- concord-server: improve `decryptString` error messages;
- concord-runner: fix export of secrets w/o password;
- concord-tasks: now uses `concord-client` instead of Resteasy.



## [0.71.0] - 2018-05-21

### Added

- ansible: limit the saved stdout/stderr size;
- concord-server: log the agent's IP address when a process starts;
- concord-server: a method to list all inventory items;
- concord-server: added optional `replace` query parameter to the
team users update operation.
- concord-console: team management UI.

### Changed

- concord-server-client: renamed to `concord-client`;
- concord-server: reduced the amount of information on the "wait
page" in the process API endpoint for browsers;
- concord-server: fixed team management RBAC;
- concord-console: fixed dropdowns not re-rendering after update;
- concord-server: fixed incorrect filtering of inventory data.



## [0.70.0] - 2018-05-17

### Added

- concord-server, concord-agent: initial support for environmental
`requirements` and agent capabilities.

### Changed

- concord-runner, concord-server: improved error handling when
working with secrets;
- concord-client: renamed to `concord-tasks`;
- concord-rpc: the KV store, heartbeat and secret gRPC services
replaced with the REST API based services.



## [0.69.0] - 2018-05-09

### Added

- concord-agent: "maintenance mode" to suspend job acquisition;
- resource-tasks: new methods to read/write JSON;
- concord-server: an "in progress" page when the process is started
using "browser" endpoints;
- concord-console: a button to download raw process log. 

### Changed

- concord-console: new UI layout.



## [0.68.1] - 2018-05-09

### Changed

- concord-server: fixed the repository connection test method not
getting secrets from the UI.



## [0.68.0] - 2018-05-04

### Added

- http-tasks: support for `PUT` requests;
- concord-server: new LDAP configuration property `usernameProperty`.
Defaults to `sAMAccountName`.
STRDTORC-507


## [0.67.0] - 2018-04-29

### Added

- concord-server-db: `bigserial` columns for forwarding log data;
- concord-runner: record task `in` parameters in process events;
- concord-runner, ansible: `correlationId` for task events;
- concord-server: `runAs` form option.



## [0.66.0] - 2018-04-22

### Added

- concord-client: support for `activeProfiles`;
- docker: ability to save `stdout` as a variable;
- concord-project-model: `retry` support for tasks;
- smtp: add support for multiple values in `to`, `cc` and `bcc`
parameters;
- concord-server: method to cancel a process including its
subprocesses;
- concord-server: method to delete an existing team.

### Changed

- concord-server: skip invalid definitions on trigger activation.  



## [0.65.3] - 2018-04-16

### Added

- concord-server: additional logging for authentication realms. 

### Changed

- concord-server: fixed session key conflicts when sub processes
are used;
- concord-console: load correct project processes;
- concord-server: fixed `platform` filter for OneOps triggers.



## [0.65.2] - 2018-04-15

### Changed

- docker: fix nginx logging configuration. 



## [0.65.1] - 2018-04-15

### Added

- concord-server: support for `cron` triggers;
- concord-project-model: better handling of YAML parsing errors;
- concord-server: process endpoints now return `childrenIds` - array
of child process IDs.

### Changed

- concord-server: fixed an issue when updating team role for an
existing team member.



## [0.64.0] - 2018-04-11

### Added

- concord-sdk: `Context#suspend` method for suspending processes with
programmatically-defined callback events;
- concord-server: metadata for organizations;
- concord-server: initial audit logging implementation;
- keywhiz: initial support;
- concord-server: support for symlinks on initial process state
ingestion;
- ansible: support for exporting secrets as `group_var` files;
- concord-console: show Ansible host events in a modal popup.
- ansible: new lookup plugins 'concord_data_secret' and 'concord_public_key_secret' 

### Changed

- ansible: deprecate `configuration.ansible`, `inventory` and
`dynamicInventory` request parameters;
- http-tasks: parse JSON responses;
- http-tasks: use `${response}` as the default out variable.



## [0.63.0] - 2018-04-01

### Added

- concord-console: new UI layout;
- concord-console: visualization of Ansible stats;
- concord-server: make `orgName` optional when `teamName` is used
when setting a resource's access level.

### Changed

- ansible: make `privateKey`'s password optional;
- concord-agent: ship `http-tasks` with the docker image; 
- concord-console: disable TLS 1.0.



## [0.62.1] - 2018-03-26

### Changed

- concord-server: send empty JSON if a project's cfg is empty;
- concord-server: use basic auth for the interactive process
endpoints.



## [0.61.0] - 2018-03-25

### Added

- concord-server, concord-agent, concord-runner: initial support for
process policies;
- concord-project-model: `exit` step to terminate execution of
the flow w/o throwing an error;
- concord-client: support for the new `startAt` parameter.

### Changed

- concord-server: OWNER access level is now required to delete a
project.



## [0.60.0] - 2018-03-22

### Added

- concord-server: a method to update access levels of inventories;
- concord-server: `startAt` process parameter to schedule process
executions;
- concord-agent: a cleanup job to remove old Docker images (keeps two
latest versions).
- concord-server: an endpoint to download a single state file.

### Changed

- concord-server: fixed an issue, preventing file upload fields from
working with custom forms.



## [0.59.0] - 2018-03-11

### Added

- concord-server: the process event endpoint now accepts `limit` and
`after` query parameters.

### Changed

- concord-server: allow removal of secrets that are in use;
- concord-server: trim usernames on login.
- ansible: the task now prepends user-provided `callback_plugins` and
`lookup_plugins` values with the default values. 



## [0.58.0] - 2018-02-25

### Added

- new task: `http`. Provides a simple HTTP client with JSON support.

### Changed

- ansible: fixed exporting of key pairs;
- concord-server: fixed handling of secret requests w/o an
organization;
- concord-server: fixed retrieval of team users.



## [0.57.0] - 2018-02-22

### Added

- slack: support for the `task` syntax, includes new messaging
options such as `icon_emoji` and `attachments`;
- ansible: filter for removing sensitive data from the logs;
- concord-client: `concord` task's `repo` is an alias for
`repository` now;
- concord-client: `project` task now works with organizations other
than `Default`;
- concord-client: `concord` task now accepts `payload` parameter
instead of `archive`, which can be either a path to a ZIP archive or
a path to a directory;
- concord-server: additional logging in case of process statup
errors.

### Changed

- concord-server, concord-console: fix handling of multi-select
dropdowns;



## [0.56.0] - 2018-02-14

### Added

- concord-server: new user's `type` attribute (`LOCAL` or `LDAP`);
- crypto: ability to export secrets from organizations other than
the current.

### Changed

- concord-docker: fixed the `unable to find user concord: no matching
entries in passwd file` issue.



## [0.55.0] - 2018-02-13

### Added

- concord-agent: the new `debug` configuration parameter to log the
resolved dependencies of a process.

### Changed
 
- concord-server: fixed incorrect inventory query filtering;
- concord-client: use polling while waiting for the process to end;
- ansible: updated to 2.4.3;
- concord-client: `org` parameter was ignored;
- concord-server: configurable max state age;
- ansible, docker: use a non-root user to run all Docker processes
(including `ansible-playbook`);
- docker: run Docker containers in the host's network.

### Breaking

- concord-server: the trigger endpoint address is made to conform
path patterns of the rest of the organization endpoints.



## [0.54.0] - 2018-02-01

### Added

- ansible, docker: support for `--add-host` in the Ansible and Docker
tasks.

### Changed

- docker: automatically update `pip` to the latest version;
- docker: more Walmart-specific CA certificates;
- concord-server: when a new team is created, automatically add the
current user as the team's `MAINTAINER`;
- concord-server: apply RBAC to the process state download endpoint.



## [0.53.0] - 2018-01-28

### Added

- concord-server: support for nested paths when retrieving
attachments;
- docker: Walmart images are now include Walmart's Root CA SSL
certificates.

### Changed

- concord-console: embed the Semantic UI resources;
- concord-server: JGit is replaced with the GIT CLI tool, improving
the support for submodules and large repositories.



## [0.52.0] - 2018-01-23

### Changed

- concord-server: avoid creation of multiple webhooks for the same
GIT repository urls registered in different projects;
- docker: add non-root users for the server and agent containers;
- dependency-manager: ignore checksums, cache the intermediate data.



## [0.51.0] - 2018-01-17

### Added

- inventory: the ansible wrapper now able to produce inventories with
per-host variables;
- crypto: a method to export a single value secret as a file;
- concord-tasks: make the organization name parameter optional for
the inventory task;
- concord-server: an endpoint to export binary data secrets;
- ansible: add a lookup plugin for retrieving "single value" secrets;
- ansible: make the org parameter optional for the inventory lookup
plugin. 

### Changed

- concord-server: form `values` are now correctly added to the
process context after the form is submitted submit;
- ansible: updated to 2.4.2;
- ansible: `inventoryFile` and `extraEnv` were ignored when the
plugin was invoked using the task syntax;
- concord-server: the "Accept payload archives" flag now correctly
updates;
- concord-server: fixed the issue preventing the process from being
marked as FAILED on YML syntax errors;
- concord-server: fixed parsing of the `activeProfiles` property when
the multipart process endpoint is used.



## [0.50.0] - 2018-01-10

### Added

- concord-server: limit the maximum allowed size of process files;
- concord-server: configurable DB connection pool size.

### Changed

- concord-console: fixed the issue with new projects overwriting the
previously opened ones. 



## [0.49.0] - 2018-01-07

### Added

- concord-server: allow hyphens and tildes in entity names;
- concord-server, concord-console: initial support for file upload
fields.

### Changed

- concord-console: the repository refresh button now opens a pop-up
window.



## [0.48.2] - 2017-12-28

### Changed

- concord-server: fixed key names in the GitHub configuration file. 



## [0.48.1] - 2017-12-27

### Added

- concord-console: the button to manually refresh a project's
repository cache.

### Changed

- concord-server: skip the repository cache if the repository's
webhook is not registered (yet);
- concord-server: fixed an bug preventing startup errors from
being logged in process logs. 



## [0.48.0] - 2017-12-17

### Added

- concord-agent: display the list of dependencies when a process
starts;
- concord-console: new "visibility" field on the process and the
secret forms;
- ansible: `skipTags` support;
- concord-server, concord-console: filter process queue by user
organizations;
- concord-console: add "organization" field to the process page;
- concord-server: new provided variables in `projectInfo`:
`repoCommitId`, `repoCommitAuthor` and `repoCommitMessage`;
- concord-console: host the swagger-ui app;
- concord-server: triggers, inventories and landing pages are moved
into organizations;
- concord-server, concord-console: an option to disallow raw payload
archives for projects;
- concord-server, concord-console: initial support for Organizations;
- concord-server: public and private projects and secrets;
- concord-server: project and secret now has owners;
- concord-server: new endpoint for managing secrets;
- concord-client: the `project` task - provides a method to create
new projects using flows;
- concord-server: methods to refresh triggers and LPs for all
projects.

### Changed

- concord-client: switched to resteasy-based client;
- concord-runner: improved stability by using a separate classloader
to load tasks;
- concord-server: user permissions are effectively replaced with the
Team RBAC feature;
- concord-console: reworked the form for creating secrets;
- concord-console: rename "Kill" button on the process page to
"Cancel".



## [0.47.0] - 2017-11-15

### Added

- concord-project-model: `debug` option for the `docker` step;
- docker: the task now automatically overrides the container's
`entrypoint`;
- concord-client: new module;
- ansible: `ansible` alias for the `ansible2` task; 
- concord-server: new automatically-provided
variable - `projectInfo`;
- concord-runner: `script` task now supports URLs;
- concord-server: initial support for process triggers.



## [0.46.0] - 2017-11-04

### Added

- kv: `getLong` and `putLong` methods;
- concord-console: "download state" button to the process status
page;  
- concord-agent, concord-server: detect orphaned or stalled
processes;
- concord-server, concord-console: process landing pages;
- concord-server: show an error page when a "portal" process fails;
- concord-project-model: alias `::` to `try:`;
- concord-console: ability to start a new process from the project's
form;
- ansible: support for saving and using Ansible's "limit" files;
- concord-server, concord-console: support for boolean form fields;
- ansible: Inventory lookup plugin.

### Changed

- concord-server: user can now create API keys only for themselves;
- concord-server: fix empty secret name in project repository
entries returned by the API;
- concord-server: added environment variable to override the
server's password.



## [0.45.1] - 2017-10-30

### Added

- concord-console: "terminated ssl" option.



## [0.45.0] - 2017-10-20

### Added

- concord-server: log the repository data when a process starts;
- concord-server: Inventory API initial support;
- concord-console: storybook integration;
- concord-server: retrieve user's LDAP info when API key
authentication is used;
- concord-server: the API keys endpoint now accepts LDAP usernames
as well as user UUIDs;
- concord-server, concord-runner: support for process OUT
variables;
- concord-server: log process status updates;
- concord-server: initial support for Teams;
- concord-server: an endpoint to retrieve a list of attachments.

### Breaking

- concord-dependency-manager: remove support for `includeOptional`.

### Changed

- concord-dependency-manager: batch resolution of Maven dependencies.



## [0.44.0] - 2017-10-12

### Added

- concord-runner: Slack task can now be called using the full form;
- concord-server: automatically remove orphaned data.

### Changed

- concord-rpc: fix dispatching of agent commands when the server is
restarted;
- ansible: throw an exception if a private key file was not found.

### Breaking

- concord-server: `/api/v1/process/{id}/subprocesses` changed to
`/api/v1/process/{id}/subprocess`.



## [0.43.0] - 2017-10-05

### Added

- ansible: ability to specify a secret name and password as a
`privateFile` value;
- concord-console: a form to create secrets;
- concord-server: validate uploaded SSH key pairs;
- concord-server, concord-agent: support for pulling dependencies
from Maven repositories;
- concord-server: update repositories using GitHub webhooks;
- concord-agent: automatic cleanup of orphaned Docker containers;
- ansible: support for additional environment variables.

### Changed

- concord-server: fixed the issue with incorrect credentials
configuration when retrieving GIT submodules.



## [0.42.0] - 2017-10-01

### Added

- concord-sdk: new provided variable `parentInstanceId`;
- concord-server: ability to suppress the execution of `onCancel` or
`onFailure` flows;
- concord-server: new API method to fork a process as its subprocess;
- concord-server: support for GIT submodules;
- concord-server, concord-console: process tags.



## [0.40.2] - 2017-09-24

### Added

- concord-server: if entry point is not set, use `default`;
- concord-project-model: alias `variables` to `configuration`;
- concord-server: pagination support for the process queue list;
- concord-project-model: support for `switch`;
- ansible: initial support for Ansible event streaming.

### Changed

- concord-server: fix Jolokia JMX names;
- concord-runner: fix `InjectVariable` for `JavaDelegate`-style
tasks;
- concord-runner: fix `JavaDelegate` handling;
- concord-server: fix SSH key pair upload/create endpoint;
- concord-server: process events cleanup;
- concord-server: fixed a potential NPE while retrieving the process
queue data.



## [0.39.0] - 2017-09-17

### Added

- concord-server, concord-runner: support for `onFailure`, `onCancel`
flows;
- concord-server, concord-runner: provide a way to access
password-protected secrets from flows;
- concord-server: allow starting a process with a POST request using
a project, a repository and an entry point specified in
`.concord.yml` file;
- concord-server: allow starting a process by sending an empty POST
request;
- concord-server, concord-console: support for `yield` for
non-custom forms;
- ansible: support for external private key files;
- ansible: support for external configuration files;
- ansible: verbosity level can now be set using the task's arguments.

### Changed

- concord-server: include GIT repository name into the cache key;
- concord-agent: remove the working directory after the process
finishes;
- concord-runner: refresh the value of `${__attr_localPath}` after
resuming a process;
- concord-server: fixed variable merging when `activeProfiles` is
an empty array or `null`.



## [0.38.3] - 2017-09-10

### Changed

- concord-runner: upgrade to the BPM engine 0.38.2. This fixes
another variable interpolation issue.



## [0.38.0] - 2017-09-07

### Added

- concord-server, concord-console: support for GIT repository paths.

### Changed

- concord-server: fix key pair generation for non-API key users.



## [0.37.0] - 2017-09-04

### Changed

- concord-agent: normalize dependency URLs, support for Nexus/WARM
redirects;
- concord-runner: fix the issue with tasks based on `concord-sdk`;
- concord-project-model: fix serialization issues when `set` task
used with nested structures.



## [0.36.0] - 2017-08-23

### Added

- concord-server: REST API method for exporting process state;
- concord-sdk: support for full-form tasks;
- concord-agent: log local IPs before starting a process.

### Changed

- concord-server: fix evaluation of variables when one variable
references another;
- concord-server: accept any type of attachment as a file, except
`text/plain`. This fixes the issue with the multipart requests with
incorrect `Content-Type`.



## [0.35.0] - 2017-08-19

### Added

- concord-sdk: new module;
- concord-runner: upgrade the BPM engine version to 0.34. This add
the support recursive value interpolation in `variables` or `in`
blocks;
- concord-agent, concord-runner: keep a pool of JVM instances
instead of starting a new one for each process.

## Changed

- concord-server: preserve user input in `data.js` after a failed
validation.



## [0.34.1] - 2017-08-14

### Changed

- concord-server: fixed an issue with custom forms and SSL.



## [0.34.0] - 2017-08-13

### Added

- concord-server: support for `shared` folders for custom forms;
- concord-server: process files should overwrite template files;
- ansible: static and dynamic inventory files can now be specified
using `inventoryFile` and `dynamicInventoryFile` task parameters.



## [0.33.0] - 2017-08-10

### Added

- concord-server: add Jolokia agent;

### Changed

- concord-runner: use copyAllCallActivityOutVariables to prevent
losing subprocess variables when events are used;
- concord-agent: slightly improved startup time of process JVMs;
- concord-server: normalize LDAP usernames;
- project-model: more robust yml-to-bpmn converter.



## [0.32.0] - 2017-08-01

### Added

- concord-runner: `@InjectVariable` annotation can now be used to
inject process context variables as task fields or method arguments.

### Changed

- ansible: fixed potential NPE.



## [0.31.0] - 2017-07-25

### Added

- project-model: support for in/out variables and `error` blocks for
process calls (aka the full form of `CallActivity`);
- ansible-tasks: a `JavaDelegate` version of the task. Allows use of
IN/OUT variables.

### Changed

- concord-server: use the native PostgreSQL UUID type.



## [0.30.1] - 2017-07-24

### Changed

- concord-server: fixed the merging of arguments while resuming a process.



## [0.30.0] - 2017-07-23

### Added

- concord-runner: environment variables support for `docker` task.
- concord-server: allow uploading arbitrary files and override
request parameters using `multipart/form-data` requests.



## [0.29.0] - 2017-07-23

### Changed

- concord-server: fixed the order of applying variable overrides;
- concord-runner: docker task now requires `/bin/sh` to be available
in all images.

### Breaking

- concord-server: storing overrides in `_defaults.json` is not
supported anymore.



## [0.28.3] - 2017-07-19

### Changed

- concord-runner: use `/workspace` instead of `/workplace` in the
docker task;

### Breaking

- concord-runner: docker task syntax is changed to
  ```
  - docker: image-name
    cmd: my-cmd
  ```



## [0.28.2] - 2017-07-18

### Changed

- concord-server: improve error handling - return error details, add
optional stacktrace field to error messages;
- concord-server: fixed NPE on retrieving an empty/non-existing log
file;
- project-model: fixed an issue with passing nested objects in
IN-parameters of tasks.



## [0.28.1] - 2017-07-14

### Changed

- concord-server: fixed an data escaping issue with project
configuration migration.



## [0.28.0] - 2017-07-13

### Added

- concord-server, concord-console: ability to override `type` in
`<input type="..."/>` for non-branded forms (e.g. for `password`
fields).

### Changed

- concord-server: fix usage of`${requestInfo}` in non-"portal"
calls.



## [0.27.0] - 2017-07-12

### Added

- concord-server: query parameters of the requests made using the
"portal" endpoint are now accessible as `requestInfo.query.param`
variables;
- concord-runner: support for running docker images using `docker`
flow command;
- docker-images: new tool image - `ansible`.

### Changed

- concord-server: fixed the issue with inability to use expressions
as default values of form fields for non-branded forms.



## [0.26.0] - 2017-07-10

### Added

- concord-server: include all available form data for "success"
and "process failed" pages.

### Changed

- concord-server: return project configuration on
`GET /api/v1/project/{projectName}` calls;
- concord-server: improve project configuration handling.



## [0.25.0] - 2017-07-10

### Added

- concord-server: form calls can now override form values and/or
provide additional data.
- concord-server: both `flows` and `processes` directories can now
be used to load flows definitions;
- concord-server: `concord.yml` can now be used instead of
`.concord.yml`;
- concord-server: profiles can now be loaded from a `profiles`
directory.



## [0.24.0] - 2017-07-05

### Added

- new task: `loadTasks`. Allows users to create their own tasks in
Groovy, store them in process files and load dynamically;
- concord-server: optional `activeProfiles` parameter can now be
used in the Portal API.

### Changed

- ansible: dependency URLs in the ansible template are temporary
changed to use WARM.



## [0.23.0] - 2017-06-28

### Added

- concord-server: Slack integration;
- concord-runner: new `slack` task to send notifications to a Slack
channel.

### Changed

- concord-server: templates now are referenced by URLs in project
configuration.



## [0.22.0] - 2017-06-18

### Added

- concord-server: support for LDAPS;
- concord-runner, concord-server: record process execution events.

### Changed

- concord-server: store agent commands in the DB;
- concord-server: store process logs in the DB;
- concord-server: fixed an issue with `data.js` generation when the
store directory does not exist.

### Breaking

- concord-server: remove the support for H2 database.



## [0.20.1] - 2017-06-08

- concord-server: fix potential connection leak.



## [0.20.0] - 2017-06-06

### Added

- concord-console: the version information page;
- concord-server: `/api/v1/server/version` API endpoint;
- docker-images: optional SSL support for the console's nginx;
- concord-server: new `PREPARING` process state;
- concord-runner: upgrade the bpm engine to 0.31.1:
  - support for EL 3.0 in flow expressions;
  - form options now can use expressions.

### Changed

- concord-server: `created` flags in the REST API responses are
replaced with `actionType: CREATED|UPDATED`. Should be more obvious.
- concord-server: fixed basic auth using passwords with `:` symbol;
- concord-console: handle `ENQUEUED` status in the default form
wizard;
- concord-server: fixed the issue with custom form redirects when
using HTTPS proxy;
- concord-server: store process state in the DB;
- concord-server: create the log file of a process as early as
possible to log startup errors.



## [0.19.0] - 2017-05-30

### Added

- concord-server: add the first batch of metrics, expose with JMX;
- concord-console: add "Test connection" button to the repository
form;
- concord-server: improve error handling for GIT repository cloning;
- concord-server: add endpoint to encrypt values with a project's key;
- concord-runner: add `crypto` task to decrypt previously encrypted
values;

### Changed

- remove "provisio" builds: use plain .sh startup scripts for the
server and the agents;
- concord-server: improve KV store to work in multi server setups;
- concord-server: simplify the project configuration handling.



## [0.18.0] - 2017-05-26

### Added

- concord-console: the project form;
- concord-server: add form field labels to the generated `data.js`
files.

### Changed

- upgrade the bpm engine to 0.29.0. It changes the form validation
messages: now it uses labels instead of field names (when available).



## [0.17.3] - 2017-05-25

### Changed

- concord-server: fixed a bug preventing LDAP attributes from being
collected.



## [0.17.2] - 2017-05-24

### Changed

- reverted back to jetty 9.2.11.v20150529 due to the issue with
serving forms using `DefaultServlet`.



## [0.17.0] - 2017-05-24

### Added

- concord-server: simple KV store backed by the database;
- concord-runner: `kv` task to use the simple KV store;
- yaml: optional error code in the `return` command;
- concord-server: ability to pull a repository using a `commitId`;
- concord-console: add the project list;
- concord-server: add project description field;
- concord-server: return process JSON objects in sync mode regardless
of success or failure;
- concord-server: log incoming gRPC connections;
- concord-server: improve request data validation for the Project REST
API.

### Changed

- concord-server: move the rpc module into the top-level directory;
- concord-console: fix timestamps on the process page;
- concord-server: suppress JOOQ banner.



## [0.16.0] - 2017-05-20

### Added

- concord-runner: add `workDir` variable (same as `__attr_localPath`);
- ansible-tasks: allow passing a vault password using request variables.
- upgrade the bpm engine to 0.28.0 which allow flows to access
available tasks using expressions `${tasks.get('name')}` or in a script
block.

### Changed

- concord-console: wrap long lines in the log viewer;
- concord-agent: do not cache `file://` and `SNAPSHOT` dependencies;
- ansible-tasks: move inline inventory processing from the server into the
plugin.



## [0.15.0] - 2017-05-19

### Added

- concord-server: process start errors now include `stacktrace` field;
- concord-server: optional synchronous mode of the process start
methods. All forms will be automatically submitted using the provided
request data;
- ansible: `defaults` replaced with `config` JSON object. Each key
represents a section in the configuration file;
- concord-runner: additional methods for `LoggingTask`.

### Changed

- concord-console: disable the log button for the processes that
don't have a log file;
- concord-agent: fixed the state transfer for failed processes;
- concord-server: make all LDAP attributes available in
`${initiator.attributes}`.



## [0.14.1] - 2017-05-17

### Changed

- concord-agent: handle early process startup errors.



## [0.14.0] - 2017-05-17

### Added

- concord-server-db: indexes for the process queue;
- yaml: `lastError` can now be used to access the last handled
`BpmnError`.

### Changed

- concord-console: fixed rendering of "Updated" and "Created"
columns in the queue table.
- concord-runner: all unhandled exceptions in a `ServiceTask`
(YAML expressions) or in a `ScriptTask` (YAML script blocks)
will be wrapped as `BpmnErrors`.



## [0.13.0] - 2017-05-16

### Added

- concord-server: process queue;
- concord-server, concord-agent: support for multiple agents, async
transport.

### Changed

- boo, nexus-perf, teamrosters and oneops plugins are moved into a
separate repository.



## [0.12.0] - 2017-05-12

## Added

- concord-console: support for "int" and "decimal" fields in the
default form renderer;
- added "project name" column to the process history;
- concord-runner: support for external scripts.

## Changed

- concord-server: fixed LDAP attributes retrieval (including
`displayName`).



## [0.11.2] - 2017-05-09

### Changed

- updated oneops-client version, fixing the `NoSuchMethodError` issue with boo-task;
- oneops-tasks: migrate to the official oneops-client.



## [0.11.1] - 2017-05-09

### Changed

- boo-task: fixed fatjar creation.



## [0.11.0] - 2017-05-09

### Added

- ansible: store exit code in the stats file.

### Changed

- concord-agent: more reliable kill command;
- ansible: allow overriding of connection timeout value.



## [0.10.0] - 2017-05-04

### Added

- boo-task: tag assembly with a cost center.



## [0.9.0] - 2017-05-04

### Added

- concord-server: GIT repository shallow cloning and caching.

### Changed

- concord-server: improved error message when creating or updating
a project with non-existing secret;
- concord-server, concord-agent: fixed logging configuration conflicts;
- concord-console: reduce wizard polling interval, "time to first form"
improved;
- runner.jar moved from the server to the agent, futher reducing the
process statup time.



## [0.8.2] - 2017-05-01

### Added

- concord-agent: simple caching mechanism for dependencies.

### Changed

- boo-task: bug fixes.



## [0.8.0] - 2017-05-01

### Added

- support for the form branding: custom forms with user-provided
HTML/CSS/JS/etc.



## [0.7.0] - 2017-04-30

### Added

- boo-task: pull more deployment information into the context;
- teamrosters-task: new task to retrieve Team Rosters data;

### Changed

- fixed: missing line/column numbers when parsing YAML process
definitions and project files;
- fixed: `value` attribute ignored in form field declarations.



## [0.6.0] - 2017-04-27

### Added

- the Console now uses LDAP authentication;
- expose (a configurable set of) LDAP attributes to processes;
- smtp: support for Mustache templates;
- yaml: simplify usage of external variables in script steps;
- boo: return deployment status from the task.



## [0.5.1] - 2017-04-20

### Changed

- boo: use all provided variables as template variables;
- fixed: `.concord.yml` variables are ignored when using a GIT
repository;
- fixed: project repositories should be ignored when starting a
process using an archive.



## [0.5.0] - 2017-04-11

### Added

- concord-runner: support for expressions in variables.

### Breaking

- project and process related constants moved to project-model module.



## [0.4.1] - 2017-04-10

### Changed

- fixed merging of profile variables with defaults and request's data.



## [0.4.0] - 2017-04-10

### Added

- support for `.concord.yml` files. Those files can contain flow and
form definitions, default variables and "profiles";
- allow overriding`dependencies` in a project file, defaults or user
requests;
- concord-console: list of secrets.

### Breaking

- concord-common: removed `Task#getKey`. Value of
`javax.inject.@Named` annotation is used to resolve a task.
- removed bpmn-format and yaml-format modules;
- yaml2-format replaced with a single module: project-model. 



## [0.3.2] - 2017-04-03

### Changed

- concord-server: merge existing process values with form values;
- concord-server: fix the LDAP mapping update method;
- concord-server: new method `/api/v1/ldap/query/{username}/group` to
retrieve a list of LDAP user's groups;
- concord-agent: fix a race condition in the log creation;
- upgrade BPM version to 0.2.1.

## Breaking

- concord-server: LDAP mappings methods moved to `/api/v1/ldap/mapping`
path prefix.



## [0.3.1] - 2017-04-02

### Changed

- concord-console: host the landing page;
- concord-console: simple process launcher using
`/#/portal/start?entryPoint=abc` URLs.

## [0.3.0] - 2017-03-31

### Changed

- fix error logging in nexus-perf tasks.
- support for single or multiple selection fields

### Breaking

- `_deps` file is replaced with `dependencies` array in a project configuration,
template, `_defaults.json` or user's request.



## [0.2.1] - 2017-03-29

### Changed

- boo upgraded to 1.0.3;
- minor fixes in examples.



## [0.2.0] - 2017-03-28

### Added

- initial support for forms, including the new UI wizard;
- the server now uses connection pooling to talk with the agent(s).
- YAML DSL support for inline JSR-223 scripts;
- now it's possible to use AD/LDAP fully-qualified usernames, e.g. `user@domain.com`;
- new endpoint: `/api/v1/role` for managing mappings between roles and permissions;
- new endpoint: `/api/v1/ldap` for managing mappings between LDAP groups and roles.

### Changed

- logging configuration cleanup;
- projects now can be created and updated using a single endpoint;
- "get user" method now uses path parameter: `GET /api/v1/user/{username}`;
- usernames containing backward slashes ("\\") are forbidden;
- unauthenticated and unauthorized errors are now returned with `Content-Type: text/plain`.

### Breaking

- removed `PUT /api/v1/user/{username}` method.



## [0.1.0] - 2017-03-22

First release.
