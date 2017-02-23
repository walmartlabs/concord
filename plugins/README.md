# Concord Plugins

Various plugin modules for Concord.

## Plugins

### Process definition providers

-  `definition-providers/fs` - process definition provider, loads process definitions
from a local directory;

### Process definition formats

- `formats/bpmn` - [bpmn.io](bpmn.io)-compatible XML format;
- `formats/yaml` - YAML-based process DSL;

### Tasks

- `tasks/ansible` - task to run an Ansible playbook;
- `tasks/jenkins` - task to trigger a Jenkins job;
- `tasks/nexus` - [nexus-perf](https://github.com/takari/nexus-perf) integration;
- `tasks/oneops` - [oneops](http://www.oneops.com/) integration;
- `tasks/smtp` - simple SMTP client as a task.

### Project templates

- `templates/ansible` - project template for running Ansible playbooks.
