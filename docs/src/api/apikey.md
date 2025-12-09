# API Key

An API Key is specific to a user and allows access to the API with the key
replacing the usage of user credentials for authentication.

The REST API provides support for a number of operations:

- [Create a New API Key](#create-key)
- [List Existing API keys](#list-keys)
- [Delete an Existing API Key](#delete-key)

<a name="create-key"/>

## Create a New API Key

Creates a new API key for a user.

* **URI** `/api/v1/apikey`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
  ```json
  {
    "username": "myLdapUsername",
    "userDomain": "optional.domain.com"
  }
  ```
* **Success response**
  ```
  Content-Type: application/json
  ```

  ```json
  {
    "ok": true,
    "id": "3b45a52f-91d7-4dd0-8bf6-b06548e0afa5",
    "key": "someGeneratedKeyValue"
  }
  ```
* **Example**: create a key, Concord will auto-generate a key name
  ```
  curl -u myLdapUser \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{ "username": "myLdapUser" }' \
  https://concord.example.com/api/v1/apikey
  ```

* **Example**: create a key, specify a key name
  ```
  curl -u myLdapUser \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{ "username": "myLdapUser", "name": "myCustomApiKeyName" }' \
  https://concord.example.com/api/v1/apikey
  ```

* **Example**: create a key when multiple users with the same username exist across domains
  ```
  curl -u myLdapUser@example.com \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{ "username": "myLdapUser", "userDomain": "example.com" }' \
  https://concord.example.com/api/v1/apikey
  ```

<a name="list-keys"/>

## List Existing API keys

Lists any existing API keys for the user. Only returns metadata, not actual keys.

* **URI** `/api/v1/apikey`
* **Method** `GET`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [
      {
        "id" : "2505acba-314d-11e9-adf9-0242ac110002",
        "userId": "aab8a8e2-2f75-4859-add1-3b8f5d7a6690", 
        "name" : "key#1"
      }, {
        "id" : "efd12c7a-3162-11e9-b9c0-0242ac110002",
        "userId": "aab8a8e2-2f75-4859-add1-3b8f5d7a6690", 
        "name" : "myCustomApiKeyName"
      }
    ]
    ```
* **Example**
  ```
  curl -u myLdapUser \
  -H "Content-Type: application/json" \
  https://concord.example.com/api/v1/apikey
  ```

<a name="delete-key"/>

## Delete an existing API key

Removes an existing API key.

* **URI** `/api/v1/apikey/${id}`
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

* **Example**
  ```
  curl -u myLdapUser \
  -X DELETE \
  -H "Content-Type: application/json" \
  https://concord.example.com/api/v1/apikey/2505acba-314d-11e9-adf9-0242ac110002
  ```

<a name="apikey-authorization"/>

# Using an API key to access the Concord API

When accessing the Concord API, the **Authorization** header can be
set with the value of an API key.  This replaces the need to authenticate
with user and password.

* **Example**
  ```
  curl \
  -H "Content-Type: application/json" \
  -H "Authorization: someGeneratedKeyValue" \
  https://concord.example.com/api/v1/apikey
  ```
