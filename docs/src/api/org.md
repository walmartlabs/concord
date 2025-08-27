# Organization

An Organization owns projects, repositories, inventories, secrets and teams.

The REST API provides support for working with organizations:

- [Create an Organization](#create-org)
- [Update an Organization](#update-org)
- [Delete an Organization](#delete-org)  
- [List Organizations](#list-org)

<a name="create-org"/>

## Create an Organization

Creates a new organization with specified parameters.

Only administrators can create new organizations.

* **URI** `/api/v1/org`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myOrg"
    }
    ```
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

<a name="update-org"/>

## Update an Organization

Updates parameters of an existing organization.

* **URI** `/api/v1/org`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "new name",
      "id": "---"
    }
    ```
    Organization `id` is mandatory, in case of updating organization `name`.

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

<a name="delete-org"/>

## Delete an Organization

Removes an existing organization and all resources associated with it
(projects, secrets, teams, etc). This operation is irreversible.

Only administrators can delete organizations.

* **URI** `/api/v1/org/${orgName}?confirmation=yes`
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

<a name="list-org"/>

## List Organizations

Lists all available organizations.

* **URI** `/api/v1/org?onlyCurrent=${onlyCurrent}`
* **Method** `GET`
* **Headers** `Authorization`
* **Parameters**
    If the `${onlyCurrent}` parameter is `true`, then the server will
    return the list of the current user's organizations. Otherwise,
    all organizations will be returned. 
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
        "name": "..."
      },
      {
        "id": "...",
        "name": "..."
       }
    ]
    ```
