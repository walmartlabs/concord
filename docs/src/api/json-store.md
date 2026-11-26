# JSON Store

The API for working with Concord [JSON Stores](../getting-started/json-store.md),
the data in stores and named queries. 

- [JSON Stores](#json-stores)
    - [Create or Update a JSON Store](#create-update-store)
    - [Get a JSON Store](#get-store)
    - [Delete a JSON Store](#delete-store)
    - [List Stores](#list-stores)
    - [Get Current Capacity for a JSON Store](#store-capacity)
    - [List Current Access Rules](#list-current-access-rules)
    - [Update Access Rules](#update-access-rules)
- [Items](#items)
    - [Create or Update an Item](#create-update-item)
    - [Get an Item](#get-item)
    - [List Items](#list-items)
    - [Delete an Item](#delete-item)
- [Queries](#queries)
    - [Create or Update a Query](#create-update-query)
    - [Get a Query](#get-query)
    - [List Queries](#list-queries)
    - [Delete a Query](#delete-query)
    - [Execute a Query](#execute-query)

## JSON Stores

<a name="create-update-store"/>

### Create or Update a JSON Store

Creates or updates a JSON Store with the specified parameters.

* **URI** `/api/v1/org/{orgName}/jsonstore`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "id": "...",
      "name": "myStore",
      "visibility": "PRIVATE",
      "owner": {
		"id": "...",
		"username": "...",
        "userDomain": "...",
        "userType": "..."
      }
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

<a name="get-store"/>

### Get a JSON Store

Returns a previously created JSON store configuration.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}`
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
      "orgId": "...",
      "orgName": "...",
      "id": "...",
      "name": "myStore",
      "visibility": "PRIVATE",
      "owner": {
        "id": "...",
        "username": "...",
        "userDomain": "...",
        "userType": "..."
      }
    }
    ```

<a name="delete-store"/>

### Delete a JSON Store

Removes an existing JSON store and all its data and associated queries.

**Warning:** the operation is irreversible.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}`
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
      "ok": true,
      "result": "DELETED"
    }
    ```

<a name="list-stores"/>

### List Stores

Lists all existing JSON stores for the specified organization.

* **URI** `/api/v1/org/${orgName}/jsonstore`
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      { "orgId":  "...", "orgName":  "...", "id": "...", "name": "...", "visibility": "...", "owner": { ... } },
      ...
    ]
    ```

<a name="store-capacity"/>

### Get Current Capacity for a JSON Store

Returns the current capacity for a specified JSON store. The `size` parameter
is the size of all items in the store and the `maxSize` is the maximum allowed
size of the store (`-1` if unbounded).

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/capacity`
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "size": "...",
      "maxSize": "..."
    }
    ```

<a name="list-current-access-rules"/>

### List Current Access Rules

Returns store's current [access rules](../getting-started/orgs.md#teams).

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/access`
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
      {"teamId": "...", "orgName": "...", "teamName":  "...", "level":  "..."},
      ...
    ]
    ```

<a name="update-access-rules"/>

### Update Access Rules

Updates stores's [access rules](../getting-started/orgs.md#teams) for a
specific team.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/access`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "teamId": "...",
      "orgName": "...",
      "teamName": "...",
      "level": "..."
    }
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "result": "UPDATED"
    }
    ```

<a name="create-update-item"/>

## Items

### Create or Update an Item

Creates or updates a JSON store items.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/item/${itemPath}`
* **Query parameters**
    - `itemPath`: a unique value to identify the data and can contain path
      separators (e.g. `dir1/dir2/item`)
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    any valid JSON object:

    ```json
    {
      ...
    }
    ```
* **Success Response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "result": "UPDATED"
    }
    ```


<a name="get-item"/>

### Get an Item

Returns a previously created JSON store item.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/item/${itemPath}`
* **Query parameters**
    - `itemPath`: item's identifier.
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    a valid JSON.

<a name="list-items"/>

### List Items

Lists items in the specified JSON store.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/item?offset=${offset}&limit=${limit}&filter=${filter}`
* **Query parameters**
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return;
    - `filter`: filters items by name (substring match, case-insensitive).
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      "item1",
      "item2",
      ...
    ]
    ```

<a name="delete-item"/>

### Delete an Item

Removes an item from the specified JSON store.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/item/${itemPath}`
* **Query parameters**
    - `itemPath`: item's identifier.
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
      "ok": true,
      "result": "DELETED"
    }
    ```

## Queries

<a name="create-update-query"/>

### Create or Update a Query

Creates a new or updates an existing [named query](../getting-started/json-store.md#named-queries).

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/query`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myQuery",
      "text": "select from ..."
    }
    ```
* **Success Response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "result": "CREATED"
    }
    ```

<a name="get-query"/>

### Get a Query

Returns a previously created [named query](../getting-started/json-store.md#named-queries).

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/query/${queryName}`
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
      "storeId": "...",
      "id": "...",
      "name": "...",
      "text": "..."
    }
    ```

<a name="list-queries"/>

### List Queries

Lists [named queries](../getting-started/json-store.md#named-queries) in
the specified JSON store.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/query?offset=${offset}&limit=${limit}&filter=${filter}`
* **Query parameters**
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return;
    - `filter`: filters queries by name (substring match, case-insensitive).
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      { "name": "...", "text": "..." },
      ...
    ]
    ```

<a name="delete-query"/>

### Delete a Query

Removes a [named query](../getting-started/json-store.md#named-queries) from
the specified JSON store.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/query/${queryName}`
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
      "ok": true,
      "result": "DELETED"
    }
    ```

<a name="execute-query"/>

### Execute a Query

Executes a previously created query using the submitted body as the query's
parameter. Returns a list of rows.

* **URI** `/api/v1/org/${orgName}/jsonstore/${storeName}/query/${queryName}/exec`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    any valid JSON object.
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      ...
    ]
    ```
