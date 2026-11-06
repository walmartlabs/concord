# Team

A team is a group of users. Users can be in multiple teams
simultaneously.

The REST API provides support for a number of operations:

- [Create a Team](#create-team)
- [Update a Team](#update-team)
- [List Teams](#list-teams)
- [List Users in a Team](#list-users)
- [Add Users to a Team](#add-users)
- [Add LDAP Groups to a Team](#add-ldap-group)
- [Remove Users from a Team](#remove-users)

<a name="create-team"/>

## Create a Team

Creates a new team with specified parameters or updates an existing one.

* **URI** `/api/v1/org/${orgName}/team`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myTeam",
      "description": "my team"
    }
    ```
    All parameters except `name` are optional.

* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "CREATED",
      "ok": true,
      "id": "..."
    }
    ```

<a name="update-team"/>

## Update a Team

Updates parameters of an existing team.

* **URI** `/api/v1/org/${orgName}/team`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "new name",
      "id": "---"
      ---
    }
    ```

    All parameters are optional.

    Omitted parameters are not updated.
    
    Team `id` is mandatory, in case of updating team `name`.

* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "UPDATED",
      "ok": true,
      "id": "..."
    }
    ```

<a name="list-teams"/>
## List Teams

Lists all existing teams.

* **URI** `/api/v1/org/${orgName}/team`
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    [
      {
        "id": "...",
        "name": "...",
        "description": "..."
      },
      {
        "id": "...",
        "name": "...",
        "description": "my project"
       }
    ]
    ```

<a name="list-users">
## List Users in a Team

Returns a list of users associated with the specified team.

* **URI** `/api/v1/org/${orgName}/team/${teamName}/users`
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      { "id": "...", "username": "..." },
      { "id": "...", "username": "..." }
    ]
    ```

<a name="add-users">
## Add Users to a Team

Adds a list of users to the specified team.

* **URI** `/api/v1/org/${orgName}/team/${teamName}/users`
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    [
      {
        "username": "userA",
        "role": "MEMBER"  
      },
      {
        "username": "userB",
        "role": "MAINTAINER"  
      },
      ...
    ]    
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true
    }
    ```

<a name="add-ldap-group">
## Add LDAP Groups to a Team

Adds a list of LDAP groups to the specified team.

* **URI** `/api/v1/org/${orgName}/team/${teamName}/ldapGroups`
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    [
      {
        "group": "CN=groupA,DC=example,DC=com",
        "role": "MEMBER"  
      },
      {
        "group": "CN=groupB,DC=example,DC=com",
        "role": "MAINTAINER"  
      },
      ...
    ]    
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true
    }
    ```

<a name="remove-users">
## Remove Users from a Team

Removes a list of users from the specified team.

* **URI** `/api/v1/org/${orgName}/team/${teamName}/users`
* **Method** `DELETE`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    ["userA", "userB", "..."]
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true
    }
    ```
