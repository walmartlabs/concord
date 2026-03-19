# Repository

Concord projects have one or multiple associated repositories. The `repository` API supports
a number of operations on the project specific repositories:

- [Create a Repository](#create-repository)
- [Update a Repository](#update-repository)
- [Delete a Repository](#delete-repository)
- [Validate a Repository](#validate-repository)
- [Refresh a Repository](#refresh-repository)

<a name="create-repository"/>

## Create a Repository

A new repository can be created with a POST request and the required parameters.

* **URI** `/api/v1/org/{orgName}/project/{projectName}/repository`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "...",
      "url": "...",
      "branch": "...",
      "commitId": "...",
      "path": "...",
      "secretId": "..."
    }
    ```

* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "CREATED",
      "ok": true
    }
    ```

<a name="update-repository"/>

## Update a Repository

An existing repository can be updated with a POST request and the changed
parameters.

* **URI** `/api/v1/org/{orgName}/project/{projectName}/repository`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "id": "...",
      "name": "...",
      "url": "...",
      "branch": "...",
      "commitId": "...",
      "path": "...",
      "secretId": "..."
    }
    ```

* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result" : "UPDATED",
      "ok" : true
    }
    ```


<a name="delete-repository"/>

## Delete a Repository

A DELETE request can be used to remove a repository.

* **URI** `/api/v1/org/{orgName}/project/{projectName}/repository/{repositoryName}`
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


<a name="validate-repository"/>

## Validate a Repository

A HTTP POST request can be used to validate a Concord repository. Specifically
this action causes the Concord YML file to be parsed and validated with regards
to syntax and any defined policies.

* **URI** `/api/v1/org/{orgName}/project/{projectName}/repository/{repositoryName}/validate`
* **Method** `POST`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "VALIDATED",
      "ok": true
    }
    ```
    
    
<a name="refresh-repository"/>

## Refresh a Repository

An existing repository can be refreshed with a POST request. This causes the
clone of the git repository stored within Concord to be updated. As a
consequence the Concord YML file is parsed again and any changes to triggers and
other configurations are updated.

* **URI** `/api/v1/org/{orgName}/project/{projectName}/repository/{repositoryName}/refresh`
* **Method** `POST`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "result": "UPDATED",
      "ok": true
    }
    ```
