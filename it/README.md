# Integration Tests

This directory contains the integration test modules for Concord.

## Commands

- To build all modules with the `docker` profile without tests, run this command:
  ```shell
  ./mvnw clean install -Pdocker -DskipTests
  ```
- To run one console integration test with dependencies that Docker manages, run this command:
  ```shell
  ./mvnw -pl it/console verify -Pit -Pdocker -Dit.test=LoginIT
  ```

Integration tests with `-Pdocker` use the local `walmartlabs/concord-server` and `walmartlabs/concord-agent` images.
The tests also use the upstream dependencies of these images.
If you change the server or agent, build the images again before you run `it/console`.
Use the full build command with the `docker` profile above.

For `it/server -Pdocker`, Docker starts the outer database, server, and agent containers.
Docker also starts a privileged Docker-in-Docker daemon.
Maven runs the tests on the host.
Only Docker task containers that Concord flows start run inside the managed daemon.
The agent uses the Unix socket of the daemon.
The daemon and agent use the same temporary directory at the same absolute path.
The test harness transfers the local `concord-ansible` image to the daemon.

## Test requirements

- Integration test resources help detect regressions.
  If possible, add flows or fixtures for more test coverage instead of changes to existing flows.
  Change an existing flow only in these conditions:
  - Support for the previous behavior has explicitly stopped.
  - The fixture is incorrect.

  Record the reason in the related change.
- The command for one console test starts Postgres, Concord Server, Concord Agent, and Selenium through Docker.
- The first `LoginIT` run can download browser images.
  Thus, the first run can take more time than later runs.
- If an `it/console` test fails, it writes screenshots to `it/console/target/screenshots/`.
- If possible, use `testcontainer-concord` for new test modules instead of the older setup pattern in `it/server`.
- The `it/agent-operator` module deploys the agent operator into a k3s testcontainer.
  It requires these local images:
  - `walmartlabs/concord-server`
  - `walmartlabs/concord-agent`
  - `walmartlabs/concord-agent-operator`

  To build the images, use the command with the `docker` profile above.
  To run the test, use this command:
  ```shell
  ./mvnw -pl it/agent-operator verify -Pit -Dit.test=AgentOperatorIT
  ```
- For prerequisites and general instructions for `-Pit`, see [../README.md](../README.md).
