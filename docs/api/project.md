# Project

## Create a new project

Creates a new project with specified parameters.

* **Permissions** `project:create`, `secret:read:${secretName}` (optional)
* **URI** `/api/v1/project`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myProject",
      "templates": ["..."],

      "repositories": {
        "myRepo": {
          "url": "...",
          "branch": "...",
          "secret": "..."
        }
      },

      "cfg": {
        ...
      }
    }
    ```
    All parameters except `name` are optional.
    
    The project configuration is a JSON object of the following structure:
    ```json
    {
      "group1": {
        "subgroup": {
          "key": "value"
        }
      },
      "group2": {
        ...
      }
    }
    ```

    Most of the parameter groups are defined by used plugins.
    
    See also: [create a new repository](#create-a-new-repository)
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true
    }
    ```

## Update an existing project

Updates parameters of an existing project.

* **Permissions** `project:update:${projectName}`, `secret:read:${secretName}` (optional)
* **URI** `/api/v1/project/${projectName}`
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "templates": ["..."],

      "repositories": {
        "myRepo": {
          "url": "...",
          "branch": "...",
          "secret": "..."
        }
      },

      "cfg": {
        ...
      }
    }
    ```
    All parameters are optional. Omitted parameters will not be updated.
    An empty value must be specified in order to remove a project's value:
    e.g. an empty `repositories` object to remove all repositories from a project.
    
    See also: [create a new repository](#create-a-new-repository), [project configuration](#project-configuration)
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true
    }
    ```

## Delete an existing project

Removes a project and its resources.

* **Permissions** `project:delete:${projectName}`
* **URI** `/api/v1/v1/project/${projectName}`
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

## List projects

Lists existing projects.

* **Permissions**
* **URI** `/api/v1/project?sortBy=${sortBy}&asc=${asc}`
* **Query parameters**
    - `sortBy`: `projectId`, `name`;
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
      { "name": "...", "templates": ["..."] },
      { "name": "..." }
    ]
    ```

## Create a new repository

Adds a new repository for a project.

* **Permissions** `project:update:${projectName}`, `secret:read:${secretName}` (optional)
* **URI** `/api/v1/project/${projectName}/repository`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myRepo",
      "url": "...",
      "branch": "...",
      "secret": "..."
    }
    ```
    
    Mandatory parameters: `name` and `url`.
    The referenced secret must exist beforehand.
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true
    }
    ```

## Update an existing repository

Updates parameters of an existing repository.

* **Permissions** `project:update:${projectName}`, `secret:read:${secretName}`
* **URI** `/api/v1/project/${projectName}/repository/${repoName}`
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "url": "...",
      "branch": "...",
      "secret": "..."
    }
    ```
    
    All parameters except `url` are optional.
    The referenced secret must exist beforehand.
* **Success response**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "ok": true
    }
    ```

## Delete an existing repository

Removes a repository.

* **Permissions** `project:update:${projectName}`
* **URI** `/api/v1/project/${projectName}/repository/${repoName}`
* **Method** `DELETE`
* **Headers** `Authorization`, `Content-Type: application/json`
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

## List repositories

Lists existing repositories in a project.

* **Permissions** `project:read:${projectName}`
* **URI** `/api/v1/project/${projectName}/repository?sortBy=${sortBy}&asc=${asc}`
* **Query parameters**
    - `sortBy`: `name`, `url`, `branch`;
    - `asc`: direction of sorting, `true` - ascending, `false` - descending
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
      { "name": "...", "url": "...", "branch": "...", "secret": "..." },
      { "name": "...", "url": "..." }
    ]
    ```

## Get project configuration

Returns project's configuration JSON or its part.

* **Permissions** `project:read:${projectName}`
* **URI** `/api/v1/project/${projectName}/cfg/${path}`
* **Query parameters**
    - `path`: path to a sub-object in the configuration, can be empty
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
      ...
    }
    ```
    
## Update an existing project configuration

Updates project's configuration or its part.

* **Permissions** `project:read:${projectName}`
* **URI** `/api/v1/project/${projectName}/cfg/${path}`
* **Query parameters**
    - `path`: path to a sub-object in the configuration, can be empty
* **Method** `PUT`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```
    Content-Type: application/json
    ```
    
    ```json
    {
      "group1": {
        "param1": 123
      }
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
