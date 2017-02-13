# Ansible

## Working with playbooks

TBD.

## Using dynamic inventories

To use a dynamic inventory script, upload it as a `dynamicInventory` value in the process call:

```
curl -v \
-H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-F request=@request.json \
-F dynamicInventory=@inventory.py \
http://localhost:8001/api/v1/process/myProject:myRepo
```

It will be marked as executable and passed directly to `ansible-playbook` command.

## Using SSH keys

The Ansible plugin supports calling a playbook with a specific SSH key.

1. Upload a SSH key pair. See the [uploading an existing key pair](../security.md#uploading-an-existing-key-pair)
document.
2. Add a private key section to a project configuration:
    ```json
    {
      "cfg": {
       "ansible": {
         "privateKeys": [
           {
             "repository": "myRepo",
             "secret": "mySshKeyPair"
           }
         ]
       }
     }
    }
    ```
    Where `repository` is the pattern matching the name of a project's repository and
    `secret` is the name of the uploaded SSH key pair.