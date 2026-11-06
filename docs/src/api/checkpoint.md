# Checkpoint

The checkpoint API can be used to list and restore
[checkpoints created in a flow](../processes-v1/flows.md#checkpoints).

- [List Checkpoints](#list)
- [Restore a Process](#restore)

<a name="list"/>

## List Checkpoints

You can access a list of all checkpoints for a specific process, identified by
the `id`, with the REST API.

* **URI** `/api/v1/process/{id}/checkpoint`
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
            "id": "...",
            "name": "...",
            "createdAt": "..."
        },
        {
            "id": "...",
            "name": "...",
            "createdAt": "..."
        },
        ...
    ]
    ```

<a name="restore"/>

## Restore a Process

You can restore a process state from a named checkpoint of a specific process
using the process identifier in the URL and the checkpoint identifier in the
body.

* **URI** `/api/v1/process/{id}/checkpoint/restore`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "id": "..."
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
