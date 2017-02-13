# Secret

## Generate a new SSH key pair

Generates a new SSH key pair. A public key will be returned in the response.

* **Permissions** `secret:create`
* **URI** `/api/v1/secret/keypair?name=${secretName}`
* **Method** `POST`
* **Headers** `Authorization`
* **Body** none
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "id": "...",
      "name": "secretName",
      "publicKey": "ssh-rsa AAAA... concord-server",
      "ok": true
    }
    ```

## Upload an existing SSH key pair

Upload an existing SSH key pair.

* **Permissions** `secret:create`
* **URI** `/api/v1/secret/keypair?name=${secretName}`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: multipart/form-data`
* **Body**
    Multipart request:
    - `public`: public key, binary data;
    - `private`: private key, binary data.
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "id": "...",
      "ok": true
    }
    ```

## Get an existing public key

Returns a public key from an existing key pair.

* **Permissions** `secret:read:${secretName}`
* **URI** `/api/v1/secret/${secretName}/public`
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
      "id": "...",
      "name": "secretName",
      "publicKey": "ssh-rsa AAAA... concord-server",
      "ok": true
    }
    ```
    
## Add a username/password secret

Adds a new secret containing username and password.

* **Permissions** `secret:create`
* **URI** `/api/v1/secret/password?name=${secretName}`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "username": "...",
      "password": "..."
    }
    ```
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "id": "...",
      "ok": true
    }
    ```

## Delete an existing secret

Removes an existing secret.

* **Permissions** `secret:delete:${secretName}`
* **URI** `/api/v1/secret/keypair?name=${secretName}`
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

## List secrets

Lists existing secrets.

* **Permissions**
* **URI** `/api/v1/secret?sortBy=${sortBy}&asc=${asc}`
* **Query parameters**
    - `sortBy`: `name`, `type`;
    - `asc`: direction of sorting, `true` - ascending, `false` - descending
* **Method** `GET`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    [
      { "id": "...", "name": "...", "type": "..." },
      { "id": "...", "name": "...", "type": "..." }
    ]
    ```