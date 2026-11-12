# Integration Tests for Concord Server/Agent

## Running

The server Docker integration tests can be run with:

```shell
./mvnw verify -Pit -Pdocker -pl it/server -Dit.test=DockerIT,DockerAnsibleIT
```

With `-Pdocker`, Docker starts the outer DB, server, and agent containers plus
a privileged Docker-in-Docker daemon. Maven runs these tests on the host; only
Docker task containers started by Concord flows run inside the managed daemon.
The daemon is accessed through a Unix socket shared with the agent, and both
share `${tmp.dir}` at the same absolute path. The harness transfers the locally
built `concord-ansible` image to the daemon.

When running a server and agent manually, configure the agent with its own
reachable Docker endpoint.

### Dependency manager

To run `DependencyManagerIT` against an existing server and agent, install its
task fixture first (commands run from the repository root):

```shell
./mvnw -pl it/tasks/dependency-manager-test clean install -DskipTests
./mvnw -pl it/server clean verify -Pit -Dit.test=DependencyManagerIT
```

Add `-Pdocker` to the second command to start Docker-managed dependencies.
If the agent runs in a bridge network, set `-Ddocker.host.addr=<host-address>`
to an address it can reach; the test's WireMock server runs on the host.

The test resolves a unique HTTP URL twice and checks that the process finishes,
both resolutions return the same path and the expected content, and WireMock
receives only one GET. Register the stub inside the test: WireMock's per-test
reset removes stubs registered in `@BeforeAll`.

## OpenLDAP tests

To run tests such as [LdapIT](./src/test/java/com/walmartlabs/concord/it/server/LdapIT.java)
locally:
- start the OpenLDAP docker container:
  ```
  $ docker run -it --rm -p 1389:389 osixia/openldap
  ```
- start the DB and Concord Agent as usual;
- run the server with the following `ldap` configuration:
  ```
  ldap {
      url = "ldap://localhost:1389"
      searchBase = "dc=example,dc=org"
      principalSearchFilter = "(cn={0})"
      userSearchFilter = "(cn=*{0}*)"
      returningAttributes = ["cn","memberof","objectClass","sn","uid"]
      usernameProperty = "cn"
      mailProperty = "mail"
      systemUsername = "cn=admin,dc=example,dc=org"
      systemPassword = "admin"
  }
  ```
- run the test with `IT_LDAP_URL` environment variable:
  ```
  IT_LDAP_URL=ldap://localhost:1389
  ```
 