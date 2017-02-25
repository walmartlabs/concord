# Process

## Starting a process

TBD.

## Stopping a process

TBD.

## Waiting for completion of a process

TBD.

## Getting status of a process

TBD.

## Downloading an attachment

Downloads a process' attachment.

* **Permissions** none
* **URI** `/api/v1/process/${instanceId}/attachment/${attachmentName}`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/octet-stream`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/octet-stream
    ```
    
    ```
    ...data...
    ```
