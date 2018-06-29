# Walmart-specific instructions

## Building

```
./mvnw clean install -Pdocker -Pwalmart
```

## Running

- check that ports `5432`, `8001`, `8101` and `8080` are available;
- create a `ldap.properties` file:
  ```
  url=ldaps://honts0102.homeoffice.wal-mart.com:3269
  searchBase=DC=Wal-Mart,DC=com
  principalSearchFilter=(&(sAMAccountName={0})(objectCategory=person))
  userSearchFilter=(&(|(sAMAccountName={0}*)(displayName={0}*))(objectCategory=person))
  usernameProperty=sAMAccountName
  systemUsername=AD_USERNAME
  systemPassword=AD_PASSWORD
  ```
- set the path to the created `ldap.properties` file:
  ```
  export LDAP_CFG=/path/to/ldap.properties
  ```
- start the containers:
  ```
  ./docker-images/run.sh
  ```
  or for OSX:
  ```
  ./docker-images/run_osx.sh
  ```

*Note*: the default scripts start PostgreSQL without a persistent
volume. All data will be lost when containers are deleted or
recreated.
