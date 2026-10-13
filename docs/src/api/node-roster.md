# Node Roster

[Node Roster](../getting-started/node-roster.md) provides access to data
gathered during [Ansible]({{ site.concord_plugins_v2_docs }}/ansible.md) playbook executions.

- [Hosts](#hosts)
    - [List Hosts With An Artifact](#list-hosts-with-an-artifact)
    - [Processes Which Deployed to a Host](#processes-which-deployed-to-a-host)
- [Facts](#facts)
    - [Latest Host Facts](#latest-host-facts)
- [Artifacts](#artifacts)
    - [Deployed Artifacts](#deployed-artifacts)

## Hosts

### List Hosts With An Artifact

Returns a paginated list of all hosts that had the specified artifact
deployed on.

* **URI** `/api/v1/noderoster/hosts?artifact=${artifactPattern}&offset=${offset}&limit=${limit}`
* **Query parameters**
    - `artifact`: regex, the artifact's URL pattern;
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return.
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    [ {
      "id" : "d18f60ec-4804-11ea-9e99-0242ac110003",
      "name" : "hostb",
      "createdAt" : "2020-02-05T10:46:52.112Z",
      "artifactUrl" : "http://localhost:57675/test.txt"
    }, {
      "id" : "d18eeb8a-4804-11ea-9e99-0242ac110003",
      "name" : "hosta",
      "createdAt" : "2020-02-05T10:46:52.109Z",
      "artifactUrl" : "http://localhost:57675/test.txt"
    } ]
    ```
  
    The result is a list of hosts where are artifact URLs matching the supplied
    `artifactPattern`

### Processes Which Deployed to a Host

Returns a (paginated) list of processes that touched the specified host.

* **URI** `/api/v1/noderoster/processes?hostName=${hostName}&hostId=${hostId}&offset=${offset}&limit=${limit}`
* **Query parameters**
    - `hostName`: name of the host;
    - `hostId`: ID of the host;
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return.

    Either `hostName` or `hostId` must be specified.
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    [ {
      "instanceId" : "5285f431-3551-4467-ad31-b43e9693eaab",
      "createdAt" : "2020-02-03T20:32:07.276Z",
      "initiatorId" : "230c5c9c-d9a7-11e6-bcfd-bb681c07b26c",
      "initiator" : "admin"
    } ]
    ```

## Facts

### Latest Host Facts

Returns the latest registered
[Ansible facts](https://docs.ansible.com/ansible/latest/user_guide/playbooks_variables.html#variables-discovered-from-systems-facts)
for the specified host.

* **URI** `/api/v1/noderoster/facts/last?hostName=${hostName}&hostId=${hostId}&offset=${offset}&limit=${limit}`
* **Query parameters**
    - `hostName`: name of the host;
    - `hostId`: ID of the host;
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return.
    
    Either `hostName` or `hostId` must be specified.
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

## Artifacts

### Deployed Artifacts

Returns a (paginated) list of known artifacts deployed to the specified host.

* **URI** `/api/v1/noderoster/artifacts?hostName=${hostName}&hostId=${hostId}&offset=${offset}&limit=${limit}`
* **Query parameters**
    - `hostName`: name of the host;
    - `hostId`: ID of the host;
    - `limit`: maximum number of records to return;
    - `offset`: starting index from which to return.
    
    Either `hostName` or `hostId` must be specified.
* **Method** `GET`
* **Headers** `Authorization`
* **Body**
    none
* **Success response**

    ```
    Content-Type: application/json
    ```

    ```json
    [ {
      "url" : "http://localhost:53705/test.txt",
      "processInstanceId" : "5285f431-3551-4467-ad31-b43e9693eaab"
    } ]
    ```
