# Integration Tests for Concord Server

## Running

```
$ ./mvnw clean install -Pdocker -Pit
```

## Running Locally

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
 