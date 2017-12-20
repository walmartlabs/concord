# Ansible

Example of getting IP addresses from OneOps to use with Ansible.

## Running

1. Upload the remote ssh key to the server:
```
curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-F private=@/path/to/id_rsa \
-F public=@/path/to/id_rsa.pub \
-F storePassword=mySecretPassword \
'http://localhost:8001/api/v1/org/Default/secret/keypair?name=mySecret'
```

The `name` should be unique. Remember `storePassword`

2. Start the process:

```
cd examples/ansible
./run.sh localhost:8001
```

3. Open the Console and find the process in the queue.

4. Open the process status page and click `Wizard` button.

5. Enter the name and the password of the secret created in the step 1. Click `Submit`.

6. Open the process' log and check the results.
