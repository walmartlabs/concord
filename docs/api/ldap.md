# LDAP group mapping

## Create a new mapping or update an existing one

Creates a new mapping with specified parameters or updates an existing one
using the specified LDAP DN.

* **Permissions** `ldapMapping:create`, `ldapMapping:update`
* **URI** `/api/v1/ldap`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "ldapDn": "CN=Team,OU=DevOps,DC=office,DC=org,DC=com",
      "roles": [
        "myRole1",
        "myRole2",
        "..."
      ]
    }
    ```
    
    Roles must exist prior to the creation of the mapping.
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true,
      "id": "..."
      "created" : false
    }
    ```
    
    The `created` paratemer indicates whether the mapping was created or updated.
    
TBD.

