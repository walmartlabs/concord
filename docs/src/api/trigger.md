# Trigger

[Triggers](../triggers/index.md) start processes in reaction to external events.

The REST API provides support for a number of operations:

- [List Triggers](#list-triggers)
- [Refresh Triggers](#refresh-triggers)


<a name="list-triggers"/>

## List Triggers

Returns a list of triggers registered for the specified project's repository.

* **URI** `/api/v2/trigger?orgName={orgName}&projectName={projectName}&repoName={repoName}&type={eventSource}`
* **Query parameters**
    - `orgName`: organization filter for trigger list;
    - `projectName`: project filter for trigger list;
    - `repoName`: repository name filter for trigger list;
    - `type`: Event source filter for trigger list (e.g. `cron`, `github`);
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
        "conditions": {
          ...
        }
      }
    ]
    ```

<a name="refresh-triggers"/>

## Refresh Triggers

Reloads the trigger definitions for the specified project's repository.

* **URI** `/api/v1/org/${orgName}/project/${projectName}/repository/${repoName}/trigger`
* **Method** `POST`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**
    none
