# Ansible

This example runs an Ansible playbook on a remote host without a project.

## Running

1. Upload the remote SSH key to the server:
```
curl -H "Authorization: API_TOKEN" \
-F private=@/path/to/id_rsa \
-F public=@/path/to/id_rsa.pub \
-F storePassword=mySecretPassword \
'http://localhost:8001/api/v1/org/Default/secret/keypair?name=mySecret'
```

Use a unique `name` value. Save the `storePassword` value.

2. Start the process:

```
cd examples/ansible_remote
./run.sh localhost:8001
```

3. Open the Console. Find the process in the queue.

4. Open the process status page. Click the `Wizard` button.

5. Enter the name and password of the secret that you created in step 1. Click `Submit`.

6. Open the process log. Check the results.
