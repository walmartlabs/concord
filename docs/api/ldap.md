# LDAP group mapping

## Create a new mapping or update an existing one

Creates a new mapping with specified parameters or updates an existing one
using the specified LDAP DN.

* **Permissions** `ldapMapping:create`, `ldapMapping:update`
* **URI** `/api/v1/ldap/mapping`
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
    
## List LDAP mappings
    
Lists existing LDAP mappings.

* **Permissions** none
* **URI** `/api/v1/ldap/mapping`
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
        "ldapDn": "...",
        "roles": ["..."]
      },
      {

      }
    ]
    ```

## Delete a LDAP mapping

Removed an existing LDAP mapping.

* **Permissions** `ldapMapping:delete`
* **URI** `/api/v1/ldap/mapping/${id}`
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
      "ok": true
    }
    ```
    
## List user's LDAP groups

Retrieves a list of user's LDAP groups.

* **Permissions** `ldap:query`
* **URI** `/api/v1/ldap/query/${username}/group`
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    ["groupA", "groupB", "..."]
    ```
