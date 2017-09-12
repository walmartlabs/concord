# Change log

## [Unreleased]

### Added

- concord-server, concord-console: support for `yield` for
non-custom forms.

### Changed

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
