# concord-client

A Concord API client based on Swagger codegen.
Build `server/impl` first to generate Swagger spec.

The temporary [OpenAPI migration test](src/test/resources/openapi/README.md) also covers the
Ansible and Node Roster specifications. Compile their server plugin modules before
running this test independently; the normal reactor build already orders them first.

Remove the migration test and its baseline extraction after completing the RESTEasy/Jakarta migration.
