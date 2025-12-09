# Tasks

Tasks are used to call Java code that implements functionality that is
too complex to express with the Concord DSL and EL in YAML directly.
Processes can include external tasks as dependencies, extending
the functionality available for Concord flows.

For example, the [Ansible]({{ site.concord_plugins_v2_docs }}/ansible.md) plugin provides
a way to execute an Ansible playbook as a flow step,
the [Docker]({{ site.concord_plugins_v2_docs }}/docker.md) plugin allows users to execute any
Docker image, etc.

In addition to the standard plugins, users can create their own tasks
leveraging (almost) any 3rd-party Java library or even wrapping existing
non-Java tools (e.g. Ansible).

Currently, Concord supports two different runtimes. The task usage and
development is different depending on the chosen runtime. See the runtime
specific pages for more details:

- [Runtime v1 tasks](../processes-v1/tasks.md)
- [Runtime v2 tasks](../processes-v2/tasks.md)
