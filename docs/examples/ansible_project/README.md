# Example: running an Ansible project

This example shows how to run an Ansible playbook using Concord.

## Playbook's repository

Create a git repository of the following structure:

```
playbook/
  hello.yml
```

The `hello.yml` playbook consists of a simple debug task:

```yaml

---
- hosts: local
  tasks:
  - debug:
      msg: "{{ greeting }}"
      verbosity: 0
```

Commit and push the repository to a remote server (e.g. GitHub). If you are using SSH, a
new SSH key pair will created on the step 2 - you will need access to the repository
settings to add a new public key.

## Concord project

Before we create our own user, all requests are perfomed using the default admin API key.
This example assumes that the `ansible` template is already uploaded to the server.

### 1. Create a new Concord project

We are going to create a new project using the `ansible` project template. The `ansible` template
will automatically add the necessary boilerplate - a workflow process definition to run our playbook
and necessary runtime dependencies.

Please refer to [the templates document](../templates.md) to find out how to upload a custom template.

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-d '{ "name": "myProject", "templates": ["ansible"] }' \
http://localhost:8001/api/v1/project
```

```json
{
  "ok": true
}
```

### 2. Create a new repository key

Please refer to the [Generate a new key pair](../../api/secret.md#generate-a-new-key-pair) document.
Use `mySecret` as a name of the key pair, it will be used on the next step.

### 3. Add a repository

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-d '{ "name": "myRepo", "url": "git@github.com:my/repo.git", "secret": "mySecret" }' \
http://localhost:8001/api/v1/project/myProject/repository
```

The `secret` parameters is the name of the key created on the step 2.

```json
{
    "ok": true
}
```

### 4. Add a new user (optional)

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-d '{ "username": "myUser", "permissions": [ "process:*:myProject" ] }' \
http://localhost:8001/api/v1/user
```

Check [the permissions description](../security.md#permissions) in the documentation.

```json
{
    "ok": true,
    "id": "9458c42e-db11-11e6-8356-07c51e4e3ef5"
}
```

### 5. Create an API key (optional)

Use the `id` value of the user created in the previous step.

```
curl -v \
-H "Content-Type: application/json" \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-d '{ "userId": "9458c42e-db11-11e6-8356-07c51e4e3ef5" }' \
http://localhost:8001/api/v1/apikey
```

```json
{
    "ok": true,
    "key": "auBy4eDWrKWsyhiDp3AQiw"
}
```

The `key` value can be used for further access to the API, e.g. to start a process.

### 6. Start a process

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
  "inventory": "myInventory",
  "extraVars": {
    "greeting": "Hello, world"
  }
}
```

Make a call:

```
curl -v \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-F request=@request.json \
-F inventory=@inventory.ini \
http://localhost:8001/api/v1/process/myProject:myRepo
```

```json
{
    "ok": true,
    "instanceId": "33c8f91e-db14-11e6-8d94-a3efec7ccd7b"
}
```

### 7. Check the logs

Use the `instanceId` value returned by the server in the previous step.

```
curl -v http://localhost:8001/logs/33c8f91e-db14-11e6-8d94-a3efec7ccd7b.log
```
