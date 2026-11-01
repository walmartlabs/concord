# concord-client

A Concord API client based on Swagger codegen.
Build `server/impl` first to generate Swagger spec.

The [OpenAPI contract test](src/test/resources/openapi/README.md) also covers the
Ansible and Node Roster specifications. Compile their server plugin modules before
running this test independently; the normal reactor build already orders them first.
