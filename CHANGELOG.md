# Change log

## Unreleased

### Added

- initial support for forms, including the new UI wizard;
- the server now uses connection pooling to talk with the agent(s).
- YAML DSL support for inline JSR-223 scripts;
- now it's possible to use AD/LDAP fully-qualified usernames, e.g. `user@domain.com`.

### Changed

- logging configuration cleanup;
- projects now can be created and updated using a single endpoint;
- "get user" method now uses path parameter: `GET /api/v1/user/{username}`;
- usernames containing backward slashes ("\\") are forbidden.

### Breaking

- removed `PUT /api/v1/user/{username}` method.



## [0.1.0] - 2017-03-22

First release.
