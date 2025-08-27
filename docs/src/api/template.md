# Template 

[Templates](../templates/index.md) allows sharing of common elements and
processes.

The REST API provides support for a number of operations:

- [Create a New Template Alias](#create-template-alias)
- [List Template Aliases](#list-template-aliases)
- [Delete a Template Alias](#delete-a-template-alias)

<a name="create-template-alias"/>

## Create a New Template Alias

Creates a new or updates existing template alias.

* **Permissions** `template:manage`
* **URI** `/api/v1/template/alias`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "alias": "my-template",
      "url": "http://host/path/my-template.jar"
    }
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

<a name="list-template-aliases"/>

## List Template Aliases

Lists existing template aliases.

* **Permissions** `template:manage`
* **URI** `/api/v1/template/alias`
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      { "alias": "my-template", "url": "http://host/port/my-template.jar"},
      { "alias": "...", "url": "..."}
    ]
    ```

<a name="delete-template-alias"/>
## Delete a Template Alias

Removes an existing template alias.

* **Permissions** `template:manage`
* **URI** `/api/v1/template/alias/${alias}`
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
