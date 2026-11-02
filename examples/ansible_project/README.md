# Example: running an Ansible project

This example runs an Ansible playbook with Concord.

## Playbook's repository

Create a Git repository with this structure:

```
playbook/
  hello.yml
```

The `hello.yml` playbook contains a simple debug task:

```yaml

---
- hosts: local
  tasks:
  - debug:
      msg: "{{ greeting }}"
      verbosity: 0
```

Commit the repository. Push the repository to a remote server, such as GitHub.
If you use SSH, step 1 creates a new SSH key pair.
Add the public key to the repository settings. You need access to those settings.

## Concord project

All requests use the default admin API key until you create a user.
This example requires the `ansible` template on the server.

### 1. Create a new repository key

Use `mySecret` as the key pair name. You will use this name in the next step.

### 2. Create a new Concord project

Create a project with the `ansible` project template.
The `ansible` template adds a workflow process definition to run the playbook.
It also adds the required runtime dependencies.

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: API_TOKEN" \
-d '{ "name": "myProject", "cfg": { "template": "ansible" }, "repositories": { "myRepo": {"url": "git@github.com:my/repo.git", "secret": "mySecret" } } }' \
http://localhost:8001/api/v1/org/Default/project
```

The `secret` parameter names the key created in step 1.

```json
{
  "ok": true
}
```


### 3. Add a new user (optional)

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: API_TOKEN" \
-d '{ "username": "myUser" }' \
http://localhost:8001/api/v1/user
```

```json
{
    "ok": true,
    "id": "9458c42e-db11-11e6-8356-07c51e4e3ef5"
}
```

### 4. Create an API key (optional)

Use the `username` value of the user that you created in the previous step.

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: API_TOKEN" \
-d '{ "username": "myUser" }' \
http://localhost:8001/api/v1/apikey
```

```json
{
    "ok": true,
    "key": "API_TOKEN"
}
```

Use the `key` value to access the API, for example, to start a process.

### 5. Start a process

Create the `inventory.ini` file:

```text
[local]
127.0.0.1

[local:vars]
ansible_connection=local
```

Create the `request.json` file:

```json
{
  "playbook": "playbook/hello.yml",
  "extraVars": {
    "greeting": "Hello, world"
  }
}
```

Send this request:

```
curl -v \
-H "Authorization: API_TOKEN" \
-F org=Default \
-F project=myProject \
-F repo=myRepo \
-F request=@request.json \
-F inventory=@inventory.ini \
http://localhost:8001/api/v1/process
```

```json
{
    "ok": true,
    "instanceId": "33c8f91e-db14-11e6-8d94-a3efec7ccd7b"
}
```

### 6. Check the logs (optional)

Use the `instanceId` value that the server returned in the previous step.
Open the UI to view the log:

```
http://localhost:8001/#/process/33c8f91e-db14-11e6-8d94-a3efec7ccd7b/log
```

### 7. Get Ansible's statistics (optional)

Use this request to download the Ansible play statistics:

```
curl -v \
-H "Authorization: API_TOKEN" \
http://localhost:8001/api/v1/process/33c8f91e-db14-11e6-8d94-a3efec7ccd7b/attachment/ansible_stats.json
```

Example response:

```json
{
  "failures": [],
  "skipped": [],
  "changed": [],
  "ok": [
    "127.0.0.1"
  ],
  "unreachable": []
}
```
