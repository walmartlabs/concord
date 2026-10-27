# Examples

## Running

In most cases, run an example with the
[Concord Command-Line Interface](./../cli). Examples that use Concord Forms need a running concord-server.
Some examples need external software, such as Ansible or Jira. Install this software before you run the example:

```
$ cd ansible
$ concord run
Starting...
16:20:00.000 [main] Using a playbook: playbook/hello.yml
16:20:00.020 [main] ANSIBLE: Can't find ansible-playbook binary in $PATH. Install a local copy or use 'dockerImage' or 'virtualenv' options.
```

## Basic

* [ansible](ansible) - Run an Ansible playbook from a workflow process.
* [datetime](datetime) - Work with dates and date/time formats.
* [forms](forms) - Use basic forms.
* [git](git) - Clone a Git repository.
* [hello_initiator](hello_initiator) - Use automatically provided variables, such as `${initiator}`.
* [hello_world2](hello_world2) - Send external parameters.
* [hello_world](hello_world) - Pass a variable to a process.
* [http](http) - Call REST endpoints.
* [in_variables](in_variables) - Use IN variables when you call a flow.
* [jira](jira) - Create an issue in Jira.
* [ldap](ldap) - Query an AD/LDAP server.
* [loglevel](loglevel) - Use different logging levels.
* [long_running](long_running) - Run a long process with the `sleep` task.
* [loops](loops) - Iterate over a collection.
* [multiple_flows](multiple_flows) - Define multiple flows in one YAML file.
* [out](out) - Use process OUT variables.
* [out_groovy](out_groovy) - Use OUT variables from Groovy scripts.
* [parsing_yaml_json](parsing_yaml_json) - Work with YAML and JSON files.
* [project_file](project_file) - Use a `concord.yml` project file.
* [slack](slack) - Send a message to a Slack channel.
* [slackChannel](slackChannel) - Manage Slack channels.
* [smtp](smtp) - Use the SMTP task.

## Intermediate

* [ansible_docker](ansible_docker) - Use a custom Ansible Docker image to run a playbook.
* [ansible_dynamic_inventory](ansible_dynamic_inventory) - Use dynamic inventory scripts in Ansible.
* [ansible_form](ansible_form) - Use forms and Ansible in one flow.
* [ansible_form_as_inventory](ansible_form_as_inventory) - Use forms to specify an Ansible inventory.
* [ansible_kerberos](ansible_kerberos) - Use Kerberos with Ansible.
* [ansible_limit](ansible_limit) - Use Ansible limit and retry files.
* [ansible_project](ansible_project) - Create and run an Ansible project.
* [ansible_remote](ansible_remote) - Run an Ansible playbook on a remote host.
* [ansible_retry](ansible_retry) - Retry an Ansible deployment for failed hosts.
* [ansible_stats](ansible_stats) - Get Ansible deployment statistics from a playbook run.
* [ansible_template](ansible_template) - Run an Ansible playbook with the Ansible template.
* [ansible_vault](ansible_vault) - Store encrypted data with Ansible Vault.
* [ansible_windows](ansible_windows) - Use Ansible with Windows hosts.
* [custom_form](custom_form) - Use forms with custom HTML, CSS, and a template library.
* [custom_form_basic](custom_form_basic) - Create a basic custom form.
* [docker](docker) - Run a Docker image with custom environment variables and arguments.
* [docker_simple](docker_simple) - Run a simple command in a Docker container.
* [error_handling](error_handling) - Handle failures.
* [external_script](external_script) - Call an external JavaScript file.
* [form_and_long_process](form_and_long_process) - Work with long-running background processes.
* [forms_override](forms_override) - Override default form values.
* [groovy](groovy) - Run a Groovy script from a flow.
* [groovy_grape](groovy_grape) - Use Groovy `@Grab` in scripts.
* [groovy_rest](groovy_rest) - Call a REST endpoint from a flow with Groovy.
* [imports](imports) - Use external Git, HTTP, and Maven resources as project files.
* [juel_java_streams](juel_java_streams) - Use expressions, Groovy, and Java Streams.
* [noderoster](noderoster) - Use the Node Roster task.
* [process_card_htmx](process_card_htmx) - Use process cards and [HTMX](https://htmx.org/) to create custom forms that start processes.
* [process_card_jquery](process_card_jquery) - Use process cards and [jQuery](https://jquery.com/) to create custom forms that start processes.
* [profiles](profiles) - Use profiles.
* [python_script](python_script) - Run a Python script from a flow.
* [ruby](ruby) - Run a Ruby snippet from a flow.
* [script_url](script_url) - Run an external script file.
* [smtp_html](smtp_html) - Send an HTML email.


## Advanced

* [ansible_out_vars](ansible_out_vars) - Save Ansible variables as Concord flow variables.
* [ansible_roles](ansible_roles) - Use external Ansible roles.
* [approval](approval) - Use forms and `runAs` to create an approval process.
* [context_injection](context_injection) - Use automatic variable injection with custom Groovy tasks.
* [custom_task](custom_task) - Create a custom Concord task (plugin).
* [dynamic_form_values](dynamic_form_values) - Add or remove values in custom forms at runtime.
* [dynamic_tasks](dynamic_tasks) - Use custom tasks that the process provides.
* [form_l10n](form_l10n) - Use custom validation error messages in forms.
* [forms_multi_group](forms_multi_group) - Restrict a form to a set of LDAP groups.
* [forms_wizard](forms_wizard) - Use multi-step forms.
* [inventory](inventory) - Use Concord Inventory to get Ansible inventory data.
* [inventory_lookup](inventory_lookup) - Use the inventory lookup plugin for Ansible.
* [process_meta](process_meta) - Export process variables as process metadata.
* [secret_files](secret_files) - Store and export secrets as files.
* [secrets](secrets) - Use Concord Secrets storage.
* [secret_lookup](secret_lookup) - Use the secret lookup plugin for Ansible.

## Runtime V2

* [runtime-v2](runtime-v2) - Use the runtime-v2 DSL and task SDK.

## Expert

* [dynamic_form_fields](dynamic_form_fields) - Define form fields at runtime.
* [dynamic_forms](dynamic_forms) - Create a form at runtime.
* [fork](fork) - Start a subprocess.
* [fork_join](fork_join) - Start multiple subprocesses and wait for them to finish.
* [generic_triggers](generic_triggers) - Use custom trigger events.
* [logback_config](logback_config) - Override the logging configuration.
* [process_from_a_process2](process_from_a_process2) - Use output variables to start a subprocess from a project.
* [process_from_a_process3](process_from_a_process3) - Start a subprocess with a directory as the payload.
* [process_from_a_process](process_from_a_process) - Start a subprocess from a flow with a payload archive.
* [mocking](mocking) - Use Groovy to replace real tasks with mock tasks for testing.
