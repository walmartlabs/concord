# Integration Tests

This directory contains Concord's integration test modules.

Commands:

- Full docker-profile build without tests:
  ```shell
  ./mvnw clean install -Pdocker -DskipTests
  ```
- Single console integration test with Docker-managed dependencies:
  ```shell
  ./mvnw -pl it/console verify -Pit -Pdocker -Dit.test=LoginIT
  ```

Notes:

- The single-test command starts Postgres, Concord Server, Concord Agent, and Selenium via Docker.
- The first `LoginIT` run may need to pull browser images, so it can be noticeably slower than subsequent runs.
- Failing `it/console` tests write screenshots to `it/console/target/screenshots/`.
- New test modules should prefer `testcontainer-concord` instead of the older setup pattern used in `it/server`.
- For broader context on prerequisites and the general `-Pit` flow, see [../README.md](../README.md).
