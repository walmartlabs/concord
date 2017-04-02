# Change log

## Unreleased

### Changed

- concord-console: host the landing page;
- concord-console: simple process launcher using `/#/portal/start?entryPoint=abc` URLs.



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
