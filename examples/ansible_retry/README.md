# Ansible

Example of running an Ansible playbook with retry (--limit <playbook>.retry).

## Running

```
cd examples/ansible
./run.sh localhost:8001
# or
./run.sh localhost:8001 retryAfterSuspend
# or
./run.sh localhost:8001 retryAfterForm
```

or

1. Prepare the payload:

```
rm -rf target && mkdir target
cp -R playbook concord.yml target/
```

2. Archive the payload:

```
cd target && zip -r payload.zip ./*
```

3. Send the payload to the server:

```
curl -v -H "Content-Type: application/octet-stream" --data-binary @payload.zip http://localhost:8001/api/v1/process
```