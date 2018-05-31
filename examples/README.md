# Examples

## Basic

* [ansible](ansible) - running an Ansible playbook from a workflow process;
* [boo](boo) - using OneOps Boo;
* [forms](forms) - using basic forms;
* [git](git) - how to clone a GIT repository;
* [hello_world](hello_world) - a simple example demonstrating how to pass a variable to a process;
* [hello_world2](hello_world2) - how to send external parameters;
* [http](http) - a simple example demonstrating how to call restful endpoints
* [in_variables](in_variables) - how to use IN-variables when calling a flow;
* [multiple_flows](multiple_flows) - multiple flows in a single YAML file;
* [jira](jira) - how to create an issue in JIRA;
* [oneops](oneops) - OneOps integration example;
* [out](out) - how to use process OUT variables;
* [out_groovy](out_groovy) - using OUT variables coming from Groovy scripts;
* [project_file](project_file) - basic usage of a `concord.yml` project file;
* [slack](slack) - sending a message to a Slack channel;
* [smtp](smtp) - using SMTP task;
* [teamrosters](teamrosters) - getting data from Team Rosters.

## Intermediate

* [ansible_docker](ansible_docker) - using a custom Ansible Docker image to run a playbook;
* [ansible_dynamic_inventory](ansible_dynamic_inventory) - using dynamic inventory scripts in Ansible;
* [ansible_form](ansible_form) - using forms and Ansible in a single flow;
* [ansible_project](ansible_project) - an example of creating an running an Ansible project;
* [ansible_remote](ansible_remote) - running an Ansible playbook an a remote host;
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
* [juel_java_steams](juel_java_steams) - using expressions, Groovy and Java Streams;
* [oneops_scaling](oneops_scaling) - how to scaling up or down a platform in OneOps;
* [profiles](profiles) - how to use profiles;
* [python_script](python_script) - running a Python script from a flow;
* [ruby](ruby) - running a Ruby snippet from a flow;
* [script_url](script_url) - running an external script file.


## Advanced

* [ansible_oneops](ansible_oneops) - how to get IP addresses from OneOps to use with Ansible;
* [approval](approval) - using forms and `runAs` to implement an approval process;
* [boo_ansible](boo_ansible) - using Boo and Ansible together;
* [context_injection](context_injection) - how to use automatic variable injection with custom tasks written in Groovy;
* [dynamic_forms](dynamic_forms) - using forms with fields added/removed dynamically;
* [dynamic_tasks](dynamic_tasks) - using custom tasks provided with the process;
* [forms_wizard](forms_wizard) - multi-step forms;
* [inventory](inventory) - using Concord Inventory to retrieve Ansible's inventory data;
* [inventory_lookup](inventory_lookup) - using the inventory lookup plugin for Ansible;
* [secret_files](secret_files) - how to store and export secrets as files;
* [secrets_lookup](secret_lookup) - using the secret lookup plugin for Ansible;
* [secrets](secrets) - working with Concord's Secrets storage;

## Expert

* [ansible_oneops_replace](ansible_oneops_replace) - using triggers to run a playbook on OneOps VM replacement events;
* [fork](fork) - starting a subprocess;
* [fork_join](fork_join) - starting multiple subprocesses and waiting for completion;
* [logback_config](logback_config) - overriding logging configuration;
* [process_from_a_process](process_from_a_process) - starting a new subprocess from a flow using a payload archive;
* [process_from_a_process2](process_from_a_process2) - using output variables, starting a new subprocess from a project.
