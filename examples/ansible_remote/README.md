# Ansible

Example of running an Ansible playbook on a remote host without creating a project.

## Running

1. Upload the remote ssh key to the server:
```
curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" \
-F private=@/path/to/id_rsa \
-F public=@/path/to/id_rsa.pub \
'http://localhost:8001/api/v1/secret/keypair?name=mySecret'
```

2. Start the process:

```
cd examples/ansible
./run.sh localhost:8001
```
