# Canary Deployment with OneOps and Ansible

The example flow picks a subset of target IPs and performs a "canary"
deployment and wait for the confirmation to perform the "full" deployment.

The actual rules of choosing "canary" deployment targets can be implemented in
different ways: a separate inventory file, as a call into an external system,
etc.

## Running

```
cd examples/ansible
./run.sh localhost:8001
```
