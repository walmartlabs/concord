# Ansible

Concord supports running [Ansible](https://www.ansible.com/) playbooks with the
`ansible` task as part of any flow. This allows you to provision and manage
application deployments with Concord.

- [Usage](#usage)
- [Ansible](#ansible)
- [Parameters](#parameters)
- [Configuring Ansible](#configuring-ansible)
- [Inline inventories](#inline-inventories)
- [Dynamic inventories](#dynamic-inventories)
- [Authentication with Secrets](#secrets)
- [Ansible Vault](#ansible-vault)
- [Custom Docker Images](#docker)
- [Retry and Limit Files](#retry-limit)
- [Ansible Lookup Plugins](#ansible-lookup-plugins)
- [Group Vars](#group-vars)
- [Input Variables](#input-variables)
- [Output Variables](#out)
- [Extra Modules](#extra-modules)
- [External Roles](#external-roles)
- [Log Filtering](#log-filtering)
- [Limitations](#limitations)

## Usage

To be able to use the task in a Concord flow, it must be added as a
[dependency](../processes-v2/configuration.html#dependencies):

```yaml
configuration:
  dependencies:
  - mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:{{ site.concord_core_version }}
```

This adds the task to the classpath and allows you to invoke the task in a flow:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: playbook/hello.yml
    out: ansibleResult
```

## Ansible

The plugin, with a configuration as above, executes an Ansible playbook with the
Ansible installation running on Concord.

__The version of Ansible being used is {{ site.concord_ansible_version }}.__

A number of configuration parameters are pre-configured by the plugin:

```
[defaults]
host_key_checking = false
retry_files_enabled = true
gather_subset = !facter,!ohai
remote_tmp = /tmp/${USER}/ansible
timeout = 120

[ssh_connection]
pipelining = true
```

Further and up to date details are available
[in the source code of the plugin]({{ site.concord_source }}blob/master/plugins/tasks/ansible/src/main/java/com/walmartlabs/concord/plugins/ansible/v2/AnsibleTaskV2.java).

One of the most important lines is `gather_subset = !facter,!ohai`. This disables
some of the variables that are usually available such as `ansible_default_ipv4`.
The parameters can be overridden in your own Ansible task invocation as
described in [Configuring Ansible](#configuring-ansible):

```yaml
- task: ansible
  in:
    config:
      defaults:
         gather_subset: all
```


## Parameters

All parameter sorted alphabetically. Usage documentation can be found in the
following sections:

- `auth` - authentication parameters:
  - `privateKey` - private key parameters;
    - `path` - string, path to a private key file located in the process's working directory;
    - `user` - string, remote username;
    - `secret` - parameters of the SSH key pair stored as a Concord secret
      - `org` - string, the secret's organization name;
      - `name` - string, the secret's name;
      - `password` - string, the secret's password (optional);
  - `krb5` - Kerberos 5 authentication:
    - `user` - AD username;
    - `password` - AD password.
- `config` - JSON object, used to create an
  [Ansible configuration](#configuring-ansible);
- `check` - boolean, when set to true Ansible does not make any changes; instead
  it tries to predict some of the changes that may occur. Check
  [the official documentation](https://docs.ansible.com/ansible/2.5/user_guide/playbooks_checkmode.html)
  for more details
- `debug` - boolean, enables additional debug logging;
- `disableConcordCallbacks` - boolean, disables all Ansible callback plugins
  provided by Concord (event recording, `outVars` processing, etc). Default is
  `false`;
- `dockerImage` - string, optional [Docker image](#custom-docker-images) to use;
- `dynamicInventoryFile` - string, path to a dynamic inventory
  script. See also [Dynamic inventories](#dynamic-inventories) section;
- `enableLogFiltering` - boolean, see [Log Filtering](#log-filtering) section;
- `enablePolicy` - boolean, apply active Concord [policies](../getting-started/policies.html#ansible-rule).
  Default is `true`;
- `enableEvents` - boolean, record Ansible events - task executions, hosts, etc.
  Default is `true`;
- `enableStats` - boolean, save the statistics as a JSON file. Default is `true`;
- `enableOutsVars` - boolean, process [output variables](#output-variables).
  Default is `true`;
- `extraEnv` - JSON object, additional environment variables
- `extraVars` - JSON object, used as `--extra-vars`. See also
  the [Input Variables](#input-variables) section;
- `extraVarsFiles` - list of strings, paths to extra variables files. See also
  the [Input Variables](#input-variables) section; 
- `groupVars` - configuration for exporting secrets as Ansible [group_vars](#group-vars) files;
- `inventory` - JSON object, an inventory data specifying
  [a static, inline inventories](#inline-inventories)section;
- `inventoryFile` - string, path to an inventory file;
- `limit` - limit file, see [Retry and Limit Files](#retry-limit)
- `playbook` - string, a path to a playbook. See [the note](#custom-docker-images)
on usage with `dockerImage`;
- `retry` - boolean, the retry flag, see [Retry and Limit Files](#retry-limit);
- `tags` - string, a comma-separated list or an array of
  [tags](http://docs.ansible.com/ansible/latest/playbooks_tags.html);
- `skipTags` - string, a comma-separated list or an array of
  [tags](http://docs.ansible.com/ansible/latest/playbooks_tags.html) to skip;
- `saveRetryFile` - file name for the retry file, see [Retry and Limit Files](#retry-limit)
- `syntaxCheck` - boolean, perform a syntax check on the playbook, but do not execute it
- `vaultPassword` - string, password to use with [Ansible Vault](#ansible-vault).
- `verbose` - integer, increase log
  [verbosity](http://docs.ansible.com/ansible/latest/ansible-playbook.html#cmdoption-ansible-playbook-v). 1-4
  correlate to -v through -vvvv.

## Result Data

In addition to
[common task result fields](../processes-v2/flows.html#task-result-data-structure),
the `ansible` task returns:

- `exitCode` - number, ansible process exit code;
- Custom attributes matching names defined in [`out_vars`](#output-variables);

## Configuring Ansible

Ansible's [configuration](https://docs.ansible.com/ansible/latest/reference_appendices/config.html)
can be specified under the  `config` key:


```yaml
flows:
  default:
  - task: ansible
    in:
      config:
        defaults:
          forks: 50
        ssh_connection:
          pipelining: True
```

which is equivalent to:

```
[defaults]
forks = 50

[ssh_connection]
pipelining = True
```

## Inline Inventories

Using an inline
[inventory](http://docs.ansible.com/ansible/latest/intro_inventory.html) you
can specify the details for all target systems  to use.

The example sets the host IP of the `local` inventory item and an
additional variable in `vars`:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: "playbook/hello.yml"
      inventory:
        local:
          hosts:
            - "127.0.0.1"
          vars:
            ansible_connection: "local"
```

Multiple inventories can be used as well:

```yaml
flows:
  default:
  - task: ansible
    in:
      inventory:
        - local:
            hosts:
              - "127.0.0.1"
            vars:
              ansible_connection: "local"
        - remote:
            hosts:
              - "example.com"
```

In the example above, the plugin creates two temporary inventory files and runs
`ansible-playbook -i fileA -i fileB ...` command.

The plugin allows mixing and matching of inventory files and inline inventory
definitions:

```yaml
flows:
  default:
  - task: ansible
    in:
      inventory:
        - "path/to/a/local/file.ini"
        - local:
            hosts:
              - "127.0.0.1"
            vars:
              ansible_connection: "local"
```

Alternatively, an inventory file can be uploaded supplied as a separate file
e.g. `inventory.ini`:

```
[local]
127.0.0.1

[local:vars]
ansible_connection=local
````

and specify to use it in `inventoryFile`:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: "playbook/hello.yml"
      inventoryFile: inventory.ini
```

## Dynamic Inventories

Alternatively to a static configuration to set the target system for Ansible,
you can use a script to create the inventory - a
[dynamic inventory](http://docs.ansible.com/ansible/latest/intro_dynamic_inventory.html).

You can specify the name of the script using the `dynamicInventoryFile` as input
parameter for the task:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: "playbook/hello.yml"
      dynamicInventoryFile: "inventory.py"
```

The script is automatically marked as executable and passed directly to
`ansible-playbook` command.


<a name="secrets"/>

## Authentication with Secrets

### Linux / SSH

The Ansible task can use a key managed as a secret by Concord, that you have
created  or uploaded  via the user interface or the
[REST API](../api/secret.html) to connect to the target servers.

The public part of a key pair should be added as a trusted key to the
target server. The easiest way to check if the key is correct is to
try to login to the remote server like this:

```
ssh -v -i /path/to/the/private/key remote_user@target_host
```

If you are able to login to the target server without any error
messages or password prompt, then the key is correct and can be used
with Ansible and Concord.

The next step is to configure the `user` to use to connect to the servers and
the key to use with the `privateKey` configuration:

```yaml
flows:
  default:
  - task: ansible
    in:
      auth:
        privateKey:
          user: "app"
          secret:
            org: "myOrg" # optional
            name: "mySecret"
            password: mySecretPassword # optional
```

This exports the key with the provided username and password to the filesystem
as `temporaryKeyFile` and uses the configured username `app` to connect. The
equivalent Ansible command is

```
ansible-playbook --user=app --private-key temporaryKeyFile ...
```

Alternatively, it is possible to specify the private key file directly:
```
- task: ansible
  in:
    auth:
      privateKey:
        path: "private.key"       
```

The `path` must be relative to the current process' working directory.

### Windows

Upload a [Windows Credential (Group Var)](https://docs.ansible.com/ansible/latest/user_guide/windows_winrm.html#ntlm) as a file secret via the UI or [api](#group-vars). 

Example file contents:
```yaml
ansible_user: AutomationUser@SUBDOMAIN.DOMAIN.COM
ansible_password: yourpasshere
ansible_port: 5985
ansible_connection: winrm
ansible_winrm_server_cert_validation: ignore
ansible_winrm_transport: ntlm
```

Export this secret as a [Group Var](#group-vars) for an inventory group containing the windows hosts.

## Ansible Vault

[Ansible Vault](https://docs.ansible.com/ansible/latest/vault.html) allows you
to keep sensitive data in files that can then be accessed in a concord flow.
The password  and the password file for Vault usage can be specified using
`vaultPassword` or  `vaultPasswordFile` parameters:

```yaml
flows:
  default:
  - task: ansible
    in:
      # passing the vault's password as a value
      vaultPassword: "myS3cr3t"

      # or as a file
      vaultPasswordFile: "get_vault_pwd.py"
```

Any secret values are then made available for usage in the Ansible playbook as
usual.

[Multiple vault passwords](https://docs.ansible.com/ansible/latest/user_guide/vault.html#multiple-vault-passwords)
or password files can also be specified:

```yaml
flows:
  default:
  - task: ansible
    in:
      # pass as values
      vaultPassword:
         myVaultID: "aStringValue"
         myOtherVaultId: "otherStringValue"

      # or using files
      vaultPasswordFile:
         vaultFile: "get_vault_pwd.py"
         otherVaultFile: "get_other_vault_pwd.py"
```

The `vaultPassword` example above is an equivalent of running

```bash
ansible-playbook --vault-id myVaultId@aStringValue --vault-id myOtherVaultId@otherStringValue ...
```

The `vaultPasswordFile` must be relative paths inside the process' working
directory.

Our [ansible_vault example project]({{ site.concord_source}}/tree/master/examples/ansible_vault)
shows a complete setup and usage.

<a name="docker"/>

## Custom Docker Images

The Ansible task typically runs on the default Docker container used by Concord
for process executions. In some cases Ansible playbooks require additional
modules to be installed. You can create a suitable Docker image, publish it to a
registry and subsequently use it in your flow by specifying it as input
parameters for the Ansible task:

```yaml
flows:
  default:
  - task: ansible
    in:
      dockerImage: "walmartlabs/concord-ansible"
```

We recommend using `walmartlabs/concord-ansible` as a base for your custom
Ansible images.

Please refer to our [Docker plugin documentation](./docker.html) for more
details.

**Note:** Concord mounts the current `${workDir}` into the container as
`/workspace`. If your `playbook` parameter specified an absolute path or uses
`${workDir}` value, consider using relative paths:

```yaml
- task: ansible
  in:
    playbook: "${workDir}/myPlaybooks/play.yml" # doesn't work, ${workDir} points to a directory outside of the container
    dockerImage: "walmartlabs/concord-ansible"

- task: ansible
  in:
    playbook: "myPlaybooks/play.yml" # works, the relative path correctly resolves to the path inside the container
    dockerImage: "walmartlabs/concord-ansible"
```

<a name="retry-limit"/>

## Retry and Limit Files

Concord provides support for Ansible "retry files". By
default, when a playbook execution fails, Ansible creates a `*.limit` file which
can be used to restart the execution for failed hosts.

If the `retry` parameter is set to `true`, Concord automatically uses the
existing retry file of the playbook:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: playbook/hello.yml      
      retry: true
```

The equivalent Ansible command is

```bash
ansible-playbook --limit @${workDir}/playbook/hello.retry
```

Note that specifying `retry: true` doesn't mean that Ansible automatically
retries the playbook execution. It only tells Ansible to look for a `*.retry`
file and if it is there - use it. If there was no `*.retry` files created before
hand, the task call simply fails. See an
[example](https://github.com/walmartlabs/concord/tree/master/examples/ansible_retry)
how to combine the plugin's `retry` and the task call's `retry` attribute to
automatically re-run a playbook.

Alternatively, the `limit` parameter can be specified directly:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: playbook/hello.yml
      # uses @${workDir}/my.retry file
      limit: @my.retry
```

The equivalent Ansible command is

```bash
ansible-playbook --limit @my.retry
```

If the `saveRetryFile` parameter is set to `true`, then the generated `*.retry`
file is saved as a process attachment and can be retrieved using the REST API:

```yaml
flows:
  default:
  - task: ansible
    in:
      saveRetryFile: true
```

```bash
curl ... http://concord.example.com/api/v1/process/${processId}/attachments/ansible.retry
```

## Ansible Lookup Plugins

Concord provides a special
[Ansible lookup plugin](https://docs.ansible.com/ansible/devel/plugins/lookup.html)
to retrieve password-protected secrets in playbooks:

<!-- use {% raw %}{% endraw %} tags to handle jinja templating -->
```yaml
{% raw %}- hosts: local
  tasks:
  - debug:
      msg: "We got {{ lookup('concord_data_secret', 'myOrg', 'mySecret', 'myPwd') }}"
      verbosity: 0{% endraw %}
```

In this example `myOrg` is the name of the organization that owns the secret,
`mySecret` is the name of the retrieved secret and `myPwd` is the password
for accessing the secret.

Use `None` to retrieve a secret created without a password:

```yaml
{% raw %}- hosts: local
  tasks:
  - debug:
      msg: "We got {{ lookup('concord_data_secret', 'myOrg', 'mySecret', None) }}"
      verbosity: 0{% endraw %}
```

If the process was started using a project, then the organization name can be
omitted. Concord will automatically use the name of the project's organization:

```yaml
{% raw %}- hosts: local
  tasks:
  - debug:
      msg: "We got {{ lookup('concord_data_secret', 'mySecret', 'myPwd') }}"
      verbosity: 0{% endraw %}
```

Currently, only simple string value secrets are supported.

See also [the example]({{ site.concord_source }}tree/master/examples/secret_lookup)
project.

<a name="group-vars"/>

## Group Vars

Files stored as Concord [secrets](../api/secret.html) can be used as Ansible's
`group_var` files.

For example, if we have a file stored as a secret like this,

```yaml
# myVars.yml
my_name: "Concord"

# saved as:
#   curl ... \
#     -F type=data \
#     -F name=myVars \
#     -F data=@myVars.yml \
#     -F storePassword=myPwd \
#     http://host:port/api/v1/org/Default/secret
```

it can be exported as a `group_vars` file using `groupVars` parameter:

```yaml
flows:
  default:
  - task: ansible
    in:
      playbook: myPlaybooks/play.yml
      ...
      groupVars:
      - myGroup:
          orgName: "Default"    # optional
          secretName: "myVars"
          password: "myPwd"     # optional
          type: "yml"           # optional, default "yml"
```

In the example above, `myVars` secret is exported as a file into
`${workDir}/myPlaybooks/group_vars/myGroup.yml` and `my_name` variable is
available for `myGroup` host group.

Check
[the official Ansible documentation](http://docs.ansible.com/ansible/latest/user_guide/intro_inventory.html#group-variables)
for more details `group_vars` files.

## Input Variables

To pass variables from the Concord flow to an Ansible playbook execution use
`extraVars`:

```yaml
- task: ansible
  in:
    playbook: playbook.yml
    extraVars:
      message: "Hello from Concord! Process ID: ${txId}"
```

And the corresponding playbook:

```yaml
- hosts: all
  tasks:
  - debug:
      msg: "{{ message }}"
      verbosity: 0
```

Effectively, it is the same as running this command:

```bash
ansible-playbook ... -e '{"message": "Hello from..."}' playbook.yml
```

Any JSON-compatible data type such as strings, numbers, booleans, lists, etc.
can be used.

Additionally, YAML/JSON files can be used to pass additional variables into the
playbook execution:

```yaml
- task: ansible
  in:
    playbook: playbook.yml
    extraVarsFiles:
      - "myVars.json"
      - "moreVars.yml"
```

This is equivalent to running the following command:

```bash
ansible-playbook ... -e @myVars.json -e @moreVars.yml playbook.yml
```

<a name="out"/>

## Output Variables

The `ansible` task can export a list of variable names from the Ansible
execution back to the Concord process context with the `outVars` parameters.

The Ansible playbook can use the `register` or `set_fact` statements to make
the variable available:

```yaml
- hosts: local
  tasks:
  - debug:
      msg: "Hi there!"
      verbosity: 0
    register: myVar
```

In the example above, the `myVar` variable saves a map of host -> value elements.
If there was a single host 127.0.0.1 in the ansible execution, then the `myVar` 
looks like the following snippet:

```json
{
   "127.0.0.1": {
      "msg": "Hi there!",
      ...
    }
}
```

The variable is captured in Concord with `outVars` and can be used after the
ansible task.

```yaml
- task: ansible
  in:
    playbook: playbook/hello.yml
    inventory:
      local:
        hosts:
          - "127.0.0.1"
        vars:
          ansible_connection: "local"
    outVars:
      - "myVar"
  out: ansibleResult
```

The object can be traversed to access specific values:

```yaml
- log: ${ansibleResult.myVar['127.0.0.1']['msg']}
```

Expressions can be used to convert an `outVar` value into a "flat" list of
values:

```yaml
# grab a 'msg' value for each host
- log: |-
    ${ansibleResult.myVar.entrySet().stream()
        .map(kv -> kv.value.msg)
        .toList()}
```

**Note:** not compatible with `disableConcordCallbacks: true` or
`enableOutVars: false`. Check the [parameters](#parameters) section for more
details.

## Extra Modules

The plugin provides two ways of adding 3rd-party modules or using a specific
version of Ansible:

- using a [custom Docker image](#custom-docker-images);
- or using the plugin's support for Python's
  [virtualenv](https://virtualenv.pypa.io/en/latest/).

Virtualenv can be used to install [PIP modules](https://pypi.org/), as well as
Ansible itself, into a temporary directory inside the process' working
directory.

For example:

```yaml
- task: ansible
  in:
    virtualenv:
      packages:
        - "ansible==2.7.0"
        - "openshift"
```

In the example above the plugin creates a new virtual environment and installs
two packages `ansible`, using the specified version, And `openshift`. This
environment is then used to run Ansible.

The full syntax:

- `virtualenv`
  - `packages` - list of PIP packages with optional version qualifiers;
  - `indexUrl` - optional URL of the Python Package Index, defaults to
    `https://pypi.org/simple`;

Note that, at the moment the plugin doesn't provide any caching for virtual
environments. Any requested modules are downloaded each time the task
executes, which might take significant amount of time depending on the size of
the packages, their dependencies, network speed, etc.

## External Roles

Ansible roles located in external repositories can be imported using the `roles`
parameter:

```yaml
- task: ansible
  in:
    playbook: "playbook.yml"
    roles:
      - src: "https://github.com/my-org/my-roles.git"
        name: "roles"
```

And the corresponding playbook:

```yaml
- hosts: myHosts
  roles:
    - somerole # any role in the repository can be used
```

Using the configuration above the plugin  performs a `git clone` of the
specified URL into a temporary directory and adds the path to `myrole` into the
path list of Ansible roles.

The `roles` parameter is a list of role imports with the following syntax:

- `src` - URL of a repository to import;
- `name` - the name of the directory or a repository shortcut (see below);
- `path` - a path in the repository to use;
- `version` - a branch name, a tag or a commit ID to use.

A shortcut can be used to avoid specifying the repository URLs multiple times:

```yaml
configuration:
  arguments:
    ansibleParams:
      defaultSrc: "https://github.com"

flows:
  default:
    - task: ansible
      in:
        playbook: playbook.yml
        roles:
          - name: "my-org/my-roles"
```

In the example above the plugin uses `ansibleParams.defaultSrc` and the role's
`name` to create the repository URL: https://github.com/my-org/my-roles.git

It is possible to put such `ansibleParams` into the [default process
configuration](../getting-started/configuration.html#default-process-variables)
and make it the system default. If you're using a hosted Concord instance,
contact your administrator if such defaults are available.

## Log Filtering

The plugin provides an optional mode when variables that might contain
sensitive data are prevented from appearing in the log.

To enable this mode, set `enableLogFiltering` to `true` in the task call
parameters:

```yaml
- task: ansible
  in:
    enableLogFiltering: true
```

If the filter detects a variable with `password`, `credentials`, `secret`,
`ansible_password` or `vaultpassword` in its name or value, then the value
appears as `******` in the log. Additionally, the `no_log` mode is enabled
for steps that include such variables.

## Limitations

Ansible's `strategy: debug` is not supported. It requires an interactive
terminal and expects user input and should not be used in Concord's
environment. Playbooks with `strategy: debug` will hang indefinitely, but can
be killed using the REST API or the Console.
