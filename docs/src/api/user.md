# User

A user represents an actual person using Concord to execute processes or
adminstrate the server.

The REST API provides support for a number of operations:

- [Create or Update a User](#create-user)
- [Find a User](#find-user)
- [Sync LDAP groups for a User](#sync-ldap-groups-user)


<a name="create-user"/>

## Create or Update a User

Creates a new user with specified parameters or updates an existing one
using the specified username.

* **URI** `/api/v1/user`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "username": "myUser",
      "type": "LOCAL",
      "roles": ["testRole1", "testRole2"]
    }
    ```
    
    Allowed `type` value:
    - `LOCAL` - a local user, can be authenticated using an [API key](./apikey.md);
    - `LDAP` - a AD/LDAP user, can be authenticated using AD/LDAP credentials or an API key.
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "id" : "9be3c167-9d82-4bf6-91c8-9e28cfa34fbb",
      "created" : false
    }
    ```

    The `created` paratemer indicates whether the user was created or updated.

<a name="find-user"/>

## Find a User

Find an existing user by name.

* **URI** `/api/v1/user/${username}`
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "id" : "...",
      "name" : "myUser"

    }
    ```

    The `created` paratemer indicates whether the user was created or updated.

<a name="sync-ldap-groups-user"/>

## Sync LDAP groups for a User

Synchronize LDAP groups for a given user.

* **URI** `/api/v1/userldapgroup/sync`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "username": "myUser",
      "userDomain": "userDomain"
    }
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
        "result": "UPDATED",
        "ok": true
    }
    ```

    The `UPDATED` result indicates the ldap groups for a specified username got synced successfully.
    <p><strong>Note</strong>: Only administrators(role: concordAdmin) can synchronize user LDAP groups</p>
