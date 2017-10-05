# Examples

## Basic

* [ansible](ansible) - running an Ansible playbook from a workflow process;
* [ansible_remote](ansible_remote) - running an Ansible playbook an a remote host;
* [forms](forms) - using basic forms;
* [hello_world](hello_world) - a simple example demostrating how to pass a variable to a process;
* [hello_world2](hello_world2) - how to send external parameters;
* [in_variables](in_variables) - how to use IN-variables when calling a flow;
* [multiple_flows](multiple_flows) - multiple flows in a single YAML file;
* [oneops](oneops) - OneOps integration example;
* [project_file](project_file) - basic usage of a `concord.yml` project file;
* [slack](slack) - sending a message to a Slack channel;
* [smtp](smtp) - using SMTP task;
* [teamrosters](teamrosters) - getting data from Team Rosters.

## Intermediate

* [ansible_docker](ansible_docker) - using a custom Ansible Docker image to run a playbook;
* [ansible_dynamic_inventory](ansible_dynamic_inventory) - using dynamic inventory scripts in Ansible;
* [ansible_form](ansible_form) - using forms and Ansible in a single flow;
* [ansible_project](ansible_project) - an example of creating an running an Ansible project;
* [ansible_template](ansible_template) - running an Ansible playbook using the Ansible template;
* [ansible_vault](ansible_vault) - using Ansible Vault to store encrypted data;
* [docker](docker) - running a docker image with custom environment and arguments;
* [docker_simple](docker_simple) - running a simple command inside of a docker container;
* [error_handling](error_handling) - how to handle failures;
* [external_script](external_script) - calling an external JavaScript file;
* [form_and_long_process](form_and_long_process) - how to deal with long "background" processes;
* [forms_brandind](forms_branding) - using forms with custom HTML/CSS;
* [forms_override](forms_override) - how to override default values in forms;
* [groovy](groovy) - running a Groovy script from a flow;
* [groovy_rest](groovy_rest) - calling a REST endpoint from a flow using Groovy;
* [python_script](python_script) - running a Python script from a flow;
* [profiles](profiles) - how to use profiles;

## Advanced

* [context_injection](context_injection) - how to use automatic variable injection with custom tasks written in Groovy;
* [dynamic_forms](dynamic_forms) - using forms with fields added/removed dynamically;
* [dynamic_tasks](dynamic_tasks) - using custom tasks provided with the process;
* [forms_wizard](forms_wizard) - multi-step forms;
* [secrets](secrets) - working with Concord's Secrets storage;

## Expert

* [fork](fork) - starting a subprocess;
* [fork_join](fork_join) - starting multiple subprocesses and waiting for completion;
* [logback_config](logback_config) - overriding logging configuration;
* [process_from_a_process](process_from_a_process) - starting a new subprocess from a flow using a payload archive;