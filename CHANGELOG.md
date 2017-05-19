# Change log

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
