# Project

A project is a container for one or more repositories, a [secret](./secret.md)
for accessing the repositories and further configuration.

The REST API provides support for a number of operations:

- [Create a Project](#create-project)
- [Update a Project](#update-project)
- [Delete a Project](#delete-project)
- [List Projects](#list-projects)
- [Get Project Configuration](#get-project-configuration)
- [Update Project Configuration](#update-project-configuration)
- [List Current Access Rules](#list-current-access-rules)
- [Update Access Rules](#update-access-rules)
- [Bulk Update Access Rules](#bulk-update-access-rules)
- [Move Project To Another Organization](#move-project-to-another-organization)
- [List Project KV store entries](#list-project-kv-store-entries)

<a name="create-project"/>

## Create a Project

Creates a new project with specified parameters.

* **URI** `/api/v1/org/${orgName}/project`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "myProject",
      "description": "my project",
    
      "repositories": {
        "myRepo": {
          "url": "...",
          "branch": "...",
          "path": "...",
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
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "result": "CREATED"
    }
    ```

**<a name="update-project"/>

## Update a Project

Updates parameters of an existing project.

* **URI** `/api/v1/org/${orgName}/project`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```json
    {
      "name": "New name",
      "id": "---",
      "description": "my updated project",
      
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
    All parameters are optional.
    
    Omitted parameters are not updated.

    Project `id` is mandatory, in case of updating project `name`, .
    
    An empty value must be specified in order to remove a project's value:
    e.g. an empty `repositories` object to remove all repositories from a project.

    See also: [project configuration](#get-project-configuration).
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "ok": true,
      "result": "UPDATED",
      "id": "---"
    }
    ```**

<a name="delete-project"/>

## Delete a Project

Removes a project and its resources.

* **URI** `/api/v1/org/${orgName}/project/${projectName}`
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

<a name="list-projects">

## List Projects

Lists all existing projects.

* **URI** `/api/v1/org/${orgName}/project`
* **Query parameters**
    - `sortBy`: `projectId`, `filter`;
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
      { "name": "..." },
      { "name": "...", "description": "my project", ... }
    ]
    ```

<a name="get-project-configuration"/>

## Get Project Configuration

Returns project's [configuration](../getting-started/projects.md#configuration) JSON or its part.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/cfg/${path}`
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

<a name="update-project-configuration"/>

## Update Project Configuration

Updates project's [configuration](../getting-started/projects.md#configuration) or its part.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/cfg/${path}`
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
      "ok": true,
      "result": "UPDATED"
    }
    ```

## List Current Access Rules

Returns project's current access rules.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/access`
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
      {"teamId": "...", "level":  "..."},
      ...
    ]
    ```

## Update Access Rules

Updates project's access rules for a specific team.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/access`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```
    Content-Type: application/json
    ```

    ```json
    {
      "teamId": "9304748c-81e6-11e9-b909-0fe0967f269a",
      "orgName": "myOrg",
      "teamName": "myTeam",
      "level": "READER"
    }
    ```
    
    Either `teamId` or `orgName` and `teamName` combinations are allowed.
    The `level` parameter accepts one of the three possible values:
    - `READER`
    - `WRITER`
    - `OWNER`
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
* **Example**
    ```
    curl -ikn -H 'Content-Type: application/json' \
    -d '{"orgName": "MyOrg", "teamName": "myTeam", "level": "READER"}' \
    http://concord.example.com/api/v1/org/MyOrg/project/MyProject/access
    ```

## Bulk Update Access Rules

Updates project's access rules for multiple teams.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/access/bulk`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```
    Content-Type: application/json
    ```

    ```json
    [{
      "teamId": "9304748c-81e6-11e9-b909-0fe0967f269a",
      "orgName": "myOrg",
      "teamName": "myTeam",
      "level": "READER"
    }]
    ```
    
    Accepts a list of access rule elements. See [the non-bulk version](#update-access-rules)
    of this method for description.
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

## Move Project to Another Organization

Moves the project to the specified Organization (through Organization name ID)

* **URI** `/api/v1/org/${orgName}/project`
* **Method** `POST`
* **Headers** `Authorization`, `Content-Type: application/json`
* **Body**
    ```
    Content-Tupe: application/json
    ```

    ```json
    {
      "name": "myProject",
      "orgName": "anotherOrg"
    }
    ```
    Also accepts `orgId` (Unique Organization ID) instead of `orgName` 
    in the request body.
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

## List Project KV store entries

List entries in a [Project KV store]({{ site.concord_plugins_v2_docs }}/key-value.md).

* **URI** `/api/v1/org/{orgName}/project/{projectName}/kv`
* **Headers** `Authorization`
* **Query parameters**
- `filter` - filters KV items by key (substring match, case-insensitive);
- `limit` - number, maximum number of records to return;
- `offset` - number, offset of the first record, used for paging. 
* **Method** `GET`
* **Body**
  none
* **Success response**
    ```
    Content-Type: application/json
    ```

    ```json
    [ {
      "key" : "myKey",
      "value" : "myValue",
      "lastUpdatedAt" : "2021-03-29T23:22:13.334+03"
     } ]
    ```
