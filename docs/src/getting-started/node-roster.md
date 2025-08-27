# Node Roster

Node Roster is an optional feature of Concord. It collects
[Ansible]({{ site.concord_plugins_v2_docs }}/ansible.md) deployment data which is exposed via the API
and a flow [task]({{ site.concord_plugins_v2_docs }}/node-roster.md).

Node Roster requires a minimum [Ansible Plugin]({{ site.concord_plugins_v2_docs }}/ansible.md)
version of 1.38.0. No further configuration is required for usage.

## Features

- automatically processes Ansible events and collects deployment data such as:
    - remote hosts and their [Ansible facts](https://docs.ansible.com/ansible/latest/user_guide/playbooks_variables.html#variables-discovered-from-systems-facts);
    - deployed [artifacts](#supported-modules);
    - deployers (users)
- provides a way to fetch the collected data using [API](../api/node-roster.md)
    or the `nodeRoster` [task]({{ site.concord_plugins_v2_docs }}/node-roster.md).

## Supported Modules

Node Roster supports the following Ansible modules:
- [get_url](https://docs.ansible.com/ansible/latest/modules/get_url_module.html)
- [maven_artifact](https://docs.ansible.com/ansible/latest/modules/maven_artifact_module.html)
- [uri](https://docs.ansible.com/ansible/latest/modules/uri_module.html)

Future versions will further extend this list. 

## Example

A simple example of a flow and a playbook that downloads a remote file and puts
it onto the remote host's directory.

The flow:
```yaml
# concord.yml
configuration:
  dependencies:
    - "mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:{{ site.concord_core_version }}"

flows:
  default:
  - task: ansible
    in:
      playbook: playbook.yml
      inventory:
        myHosts:
          hosts:
            - "myhost.example.com"
          vars:
            ansible_connection: "local" # just for example purposes, don't actually connect
      extraVars:
        artifactDest: "${workDir}"
```

The playbook:
```yaml
# playbook.yml
---
- hosts: myHosts
  tasks:
  - get_url:
      url: "http://central.maven.org/maven2/com/walmartlabs/concord/concord-cli/{{ site.concord_core_version }}/concord-cli-{{ site.concord_core_version }}-executable.jar"
      dest: "{% raw %}{{ artifactDest }}{% endraw %}"
```

To run the example, either put it into a Git repository and follow
the [Quick Start guide](../getting-started/quickstart.md) or start it using `curl` (in the directory with
`concord.yml` and `playbook.yml`):
```
$ curl -i -u CONCORD_USER \
-F concord.yml=@concord.yml \
-F playbook.yml=@playbook.yml \
https://concord.example.com/api/v1/process 
```

Open the Concord UI to check the process status. After the process finishes,
try one of the Node Roster endpoints:
```
$ curl -i -u CONCORD_USER https://concord.example.com/api/v1/noderoster/artifacts?hostName=myhost.example.com
HTTP/1.1 200 OK
...
[ {
  "url" : "http://central.maven.org/maven2/com/walmartlabs/concord/concord-cli/{{ site.concord_core_version }}/concord-cli-{{ site.concord_core_version }}-executable.jar"
} ]
```

The API endpoint in the example returns a list of artifacts that were deployed
to the specified host. Check [the API documentation](../api/node-roster.md)
for the complete list of endpoints.
