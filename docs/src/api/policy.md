# Policy

[Policies](../getting-started/policies.md) control various aspects of process
execution.

The REST API provides support for working with policies:

- [Create or Update a Policy](#create-or-update-a-policy)
- [Get a Policy](#get-a-policy)
- [Remove a Policy](#remove-a-policy)
- [Link a Policy](#link-a-policy)
- [Unlink a Policy](#unlink-a-policy)

<a name="create-update"/>

## Create or Update a Policy

Creates a new policy or updates an existing one. Requires administrator
privileges.

* **URI** `/api/v2/policy`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myPolicy",
      "parentId": "...",
      "rules": {
        ...policy document...      
      }
    }
    ```
    
    - `name` - the policy's name;
    - `parentId` - optional, ID of a parent policy;
    - `rules` - the policy's rules, see the
    [Policies](../getting-started/policies.md) document.
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

<a name="get"/>

## Get a Policy

Returns an existing policy.

* **URI** `/api/v2/policy/${name}`
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
      "name": "myPolicy",
      "parentId": "...",
      "rules": {
        ...policy document...      
      }
    }
    ```

<a name="delete"/>

## Remove a Policy

Deletes an existing policy.

* **URI** `/api/v2/policy/${name}`
* **Method** `DELETE`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "DELETED",
      "ok": true
    }
    ```

<a name="link"/>

## Link a Policy

Links an existing policy to an organization, project or a specific user.

* **URI** `/api/v2/policy/${name}/link`
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "orgName": "myOrg",
      "projectName": "myProject",
      "userName": "someUser"
    }
    ```
    
    All parameters are optional. If all parameters are omitted (or `null`) then
    the policy becomes a system-wide policy.
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

<a name="unlink"/>

## Unlink a Policy

Unlinks an existing policy from an organization, project or a specific user.

* **URI** `/api/v2/policy/${name}/link?orgName=${orgName}&projectName=${projectName}&userName=${userName}`
* **Query parameters**
    All parameters are optional. If all parameters are omitted then the system
    link is removed. 
* **Method** `DELETE`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    none       
* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "DELETED",
      "ok": true
    }
    ```
