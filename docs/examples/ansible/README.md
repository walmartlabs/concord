# Ansible

Example of running an Ansible playbook without creating a project.

## Running

```
cd docs/examples/ansible
./run.sh localhost:8001
```

or

1. Prepare the payload:

```
rm -rf target && mkdir target
cp -R playbook processes _main.json target/
```

2. Archive the payload:

```
cd target && zip -r payload.zip ./*
```

3. Send the payload to the server:

```
curl -v -H "Content-Type: application/octet-stream" --data-binary @payload.zip http://localhost:8001/api/v1/process
```