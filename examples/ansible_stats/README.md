# Ansible

Example of running an Ansible playbook without creating a project.
The runtime-v2 Ansible task returns registered variables in `ansibleResult`; this example
logs its `_stats` field.

## Running

```
cd examples/ansible_stats
./run.sh localhost:8001
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
curl -v -u username -F archive=@payload.zip http://localhost:8001/api/v1/process
```
