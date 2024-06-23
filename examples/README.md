# Examples

## Running

Generally, examples can be executed using
[Concord Command-Line Interface](./../cli). Examples that use Concord Forms
require a running concord-server. Some examples may require external
dependencies such as Ansible, JIRA and others to be installed:

```
$ cd ansible
$ concord run
Starting...
16:20:00.000 [main] Using a playbook: playbook/hello.yml
16:20:00.020 [main] ANSIBLE: Can't find ansible-playbook binary in $PATH. Install a local copy or use 'dockerImage' or 'virtualenv' options.
```

## Basic

* [ansible](ansible) - running an Ansible playbook from a workflow process;
* [datetime](datetime) - how to work with dates and date/time formats;
* [forms](forms) - using basic forms;
* [git](git) - how to clone a GIT repository;
* [hello_initiator](hello_initiator) - a simple example that shows how to use automatically provided variables such as `${initiator}`;
* [hello_world2](hello_world2) - how to send external parameters;
* [hello_world](hello_world) - a simple example demonstrating how to pass a variable to a process;
* [http](http) - a simple example demonstrating how to call restful endpoints
* [in_variables](in_variables) - how to use IN-variables when calling a flow;
* [jira](jira) - how to create an issue in JIRA;
* [ldap](ldap) - how to query an AD/LDAP server;
* [long_running](long_running) - a simple example of a long-running process using `sleep` task;
* [looper](looper) - triggering Looper jobs from Concord;
* [loops](loops) - how to iterate a collection;
* [multiple_flows](multiple_flows) - multiple flows in a single YAML file;
* [out](out) - how to use process OUT variables;
* [out_groovy](out_groovy) - using OUT variables coming from Groovy scripts;
* [parsing_yaml_json](parsing_yaml_json) - how to work with YAML and JSON files;
* [project_file](project_file) - basic usage of a `concord.yml` project file;
* [slack](slack) - sending a message to a Slack channel;
* [slackChannel](slackChannel) - how to manage Slack channels;
* [smtp](smtp) - using SMTP task.

## Intermediate

* [ansible_docker](ansible_docker) - using a custom Ansible Docker image to run a playbook;
* [ansible_dynamic_inventory](ansible_dynamic_inventory) - using dynamic inventory scripts in Ansible;
* [ansible_form](ansible_form) - using forms and Ansible in a single flow;
* [ansible_form_as_inventory](ansible_form_as_inventory) - using forms to specify an Ansible inventory;
* [ansible_limit](ansible_limit) - how to use Ansible's limit/retry files;
* [ansible_project](ansible_project) - an example of creating an running an Ansible project;
* [ansible_remote](ansible_remote) - running an Ansible playbook an a remote host;
* [ansible_retry](ansible_retry) - how to automatically retry Ansible deployment for failed hosts;
* [ansible_stats](ansible_stats) - shows how to get Ansible deployment stats back from a playbook run;
* [ansible_template](ansible_template) - running an Ansible playbook using the Ansible template;
* [ansible_vault](ansible_vault) - using Ansible Vault to store encrypted data;
* [docker](docker) - running a docker image with custom environment and arguments;
* [docker_simple](docker_simple) - running a simple command inside of a docker container;
* [error_handling](error_handling) - how to handle failures;
* [external_script](external_script) - calling an external JavaScript file;
* [form_and_long_process](form_and_long_process) - how to deal with long "background" processes;
* [custom_form](custom_form) - using forms with custom HTML/CSS and a templating library;
* [custom_form_basic](custom_form_basic) - a basic example of a custom form;
* [forms_override](forms_override) - how to override default values in forms;
* [groovy](groovy) - running a Groovy script from a flow;
* [groovy_grape](groovy_grape) - how to use Groovy's `@Grab` in scripts;
* [groovy_rest](groovy_rest) - calling a REST endpoint from a flow using Groovy;
* [imports](imports) - how to use external GIT/http/mvn resources as project files;
* [juel_java_steams](juel_java_steams) - using expressions, Groovy and Java Streams;
* [profiles](profiles) - how to use profiles;
* [process_card_htmx](process_card_htmx) - how to use "process cards" and [HTMX](https://htmx.org/) to implement custom forms to start processes;
* [process_card_jquery](process_card_jquery) - how to use "process cards" and [jQuery](https://jquery.com/) to implement custom forms to start processes;
* [python_script](python_script) - running a Python script from a flow;
* [ruby](ruby) - running a Ruby snippet from a flow;
* [script_url](script_url) - running an external script file;
* [smpt_html](smtp_html) - how to send a HTML email.


## Advanced

* [ansible_gatekeeper](ansible_gatekeeper) - using Gatekeeper to gate an Ansible deployment;
* [ansible_out_vars](ansible_out_vars) - saving Ansible variables as Concord flow variables;
* [ansible_roles](#ansible_roles) - how to use external Ansible roles;
* [approval](approval) - using forms and `runAs` to implement an approval process;
* [context_injection](context_injection) - how to use automatic variable injection with custom tasks written in Groovy;
* [custom_task](custom_task) - how to create a custom Concord task (plugin);
* [dynamic_form_values](dynamic_form_values) - using custom forms with values added/removed dynamically;
* [dynamic_tasks](dynamic_tasks) - using custom tasks provided with the process;
* [form_l10n](form_l10n) - how to use custom validation error messages in forms;
* [forms_multi_group](forms_multi_group) - how to restrict a form to a set of LDAP groups;
* [forms_wizard](forms_wizard) - multi-step forms;
* [inventory](inventory) - using Concord Inventory to retrieve Ansible's inventory data;
* [inventory_lookup](inventory_lookup) - using the inventory lookup plugin for Ansible;
* [process_meta](process_meta) - exporting process variables as process metadata;
* [secret_files](secret_files) - how to store and export secrets as files;
* [secrets_lookup](secret_lookup) - using the secret lookup plugin for Ansible;
* [secrets](secrets) - working with Concord's Secrets storage.

## Expert

* [ansible_gatekeeper](ansible_gatekeeper) - how to forbid execution of certain Ansible steps;
* [dynamic_forms](dynamic_forms) - how to create a form dynamically (in runtime);
* [fork](fork) - starting a subprocess;
* [fork_join](fork_join) - starting multiple subprocesses and waiting for completion;
* [generic_triggers](generic_triggers) - how to use custom trigger events;
* [logback_config](logback_config) - overriding logging configuration;
- [mocking](mocking) - how to use Groovy to replace "real" tasks with "mock" versions for testing;
* [process_from_a_process](process_from_a_process) - starting a new subprocess from a flow using a payload archive;
* [process_from_a_process2](process_from_a_process2) - using output variables, starting a new subprocess from a project;
* [process_from_a_process3](process_from_a_process3) - starting a new subprocess using a directory as the payload.
