# Role

## Create a new role or update an existing one

Creates a new role with specified parameters or updates an existing one
using the specified name.

* **Permissions** `role:create`, `role:update`
* **URI** `/api/v1/role`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myRole",
      "permissions": [
        "project:create",
        "process:start:*",
        "..."
      ]
    }
    ```
    See also [the list of available permissions](../security.md#permissions).
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true,
      "created" : false
    }
    ```
    
    The `created` paratemer indicates whether the role was created or updated.
    
TBD.

