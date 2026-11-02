# Integration Tests for Concord Server/Agent

## Running

Run the server Docker integration tests with:

```shell
./mvnw verify -Pit -Pdocker -pl it/server -Dit.test=DockerIT,DockerAnsibleIT
```

With `-Pdocker`, Docker starts the outer DB, Server, and Agent containers, plus
a privileged Docker-in-Docker daemon. Maven runs the tests on the host.
Concord flows start Docker task containers inside the managed daemon only.
The agent accesses the daemon through a shared Unix socket. The agent and
daemon share `${tmp.dir}` at the same absolute path. The harness transfers the
locally built `concord-ansible` image to the daemon.

When you run a Server and Agent manually, configure the Agent to use a Docker
endpoint that it can reach.

## OpenLDAP tests

To run tests such as [LdapIT](./src/test/java/com/walmartlabs/concord/it/server/LdapIT.java)
locally:
- Start the OpenLDAP Docker container:
  ```
  $ docker run -it --rm -p 1389:389 osixia/openldap
  ```
- Start the DB and Concord Agent as usual.
- Run the Server with this `ldap` configuration:
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
- Run the test with the `IT_LDAP_URL` environment variable:
  ```
  IT_LDAP_URL=ldap://localhost:1389
  ```
 