# Change log

## [Unreleased]

### Changed

- concord-server: now process state snapshots can only be accessed by
admins, process owners and "global readers". 



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

- http: support for `PUT` requests;
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
- http: parse JSON responses;
- http: use `${response}` as the default out variable.



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
- concord: make the organization name parameter optional for the
inventory task; 
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
