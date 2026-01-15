# Overview

- [Directory Structure](#directory-structure)
- [Additional Concord Files](#additional-concord-files)
- [DSL](#dsl)
- [Public Flows](#public-flows)
- [Variables](#variables)
    - [Provided Variables](#provided-variables)
    - [Output Variables](#output-variables)
- [Dry-run mode](#dry-run-mode)
    - [Running a Flow in Dry-Run Mode](#running-a-flow-in-dry-run-mode)
    - [Behavior of Tasks and Scripts in Dry-Run mode](#behavior-of-tasks-and-scripts-in-dry-run-mode)

**Note:** if you used Concord before, check [the migration guide](./migration.md).
It describes key differences between Concord flows v1 and v2.

## Directory Structure

Regardless of how the process starts -- using
[a project and a Git repository](../api/process.md#form-data) or by
[sending a payload archive](../api/process.md#zip-file), Concord assumes
a certain structure of the process's working directory:

- `concord.yml` - a Concord [DSL](#dsl) file containing the main flow,
configuration, profiles and other declarations;
- `concord/**/*.concord.yml` - directory containing
[extra Concord YAML files](#additional-concord-files);
- `forms` - directory with [custom forms](../getting-started/forms.md#custom).

Anything else is copied as-is and available for the process.
[Plugins]({{ site.concord_plugins_v2_docs }}/index.md) can require other files to be present in
the working directory.

The same structure should be used when storing your project in a Git repository.
Concord clones the repository and recursively copies the specified directory
[path](../api/repository.md#create-a-repository) (`/` by default which includes
all files in the repository) to the working directory for the process. If a
subdirectory is specified in the Concord repository's configuration, any paths
outside the configuration-specified path are ignored and not copied. The repository
name it _not_ included in the final path.

## Additional Concord Files

The default use case with the Concord DSL is to maintain everything in the one
`concord.yml` file. The usage of a `concord` folder and files within it allows
you to reduce the individual file sizes.

`./concord/test.concord.yml`:

```yaml
configuration:
  arguments:
    nested:
      name: "stranger"

flows:
  default:
  - log: "Hello, ${nested.name}!"
```
  
`./concord.yml`:

```yaml
configuration:
  arguments:
    nested:
      name: "Concord"
```

The above example prints out `Hello, Concord!`, when running the default flow.

Concord folder merge rules:

- Concord loads `concord/**/*.concord.yml` files in alphabetical order,
  including subdirectories;
- flows and forms with the same names are overridden by their counterpart from
  the files loaded previously;
- all triggers from all files are added together. If there are multiple trigger
  definitions across several files, the resulting process contains all of
  them;
- configuration values are merged. The values from the last loaded file override
  the values from the files loaded earlier;
- profiles with flows, forms and configuration values are merged according to
  the rules above.

The path to additional Concord files can be configured using
[the resources block](./resources.md).

## DSL

Concord DSL files contain [configuration](./configuration.md),
[flows](./flows.md), [profiles](./profiles.md) and other declarations.

The top-level syntax of a Concord DSL file is:

```yaml
configuration:
  ...

flows:
  ...

publicFlows:
  ...

forms:
  ...

triggers:
  ...

profiles:
  ...

resources:
  ...

imports:
  ...
```

Let's take a look at each section:
- [configuration](./configuration.md) - defines process configuration,
dependencies, arguments and other values;
- [flows](./flows.md) - contains one or more Concord flows;
- [publicFlows](#public-flows) - list of flow names which may be used as an [entry point](./configuration.md#entry-point);
- [forms](../getting-started/forms.md) - Concord form definitions;
- [triggers](../triggers/index.md) - contains trigger definitions;
- [profiles](./profiles.md) - declares profiles that can override
declarations from other sections;
- [resources](./resources.md) - configurable paths to Concord resources;
- [imports](./imports.md) - allows referencing external Concord definitions.

## Public Flows

Flows listed in the `publicFlows` section are the only flows allowed as
[entry point](./configuration.md#entry-point) values. This also limits the
flows listed in the repository run dialog. When the `publicFlows` is omitted,
Concord considers all flows as public.

Flows from an [imported repository](./imports.md) are subject to the same
setting. `publicFlows` defined in the imported repository are merged
with those defined in the main repository.

```yaml
publicFlows:
  - default
  - enterHere

flows:
  default:
    - log: "Hello!"
    - call: internalFlow

  enterHere:
    - "Using alternative entry point."

  # not listed in the UI repository start popup
  internalFlow:
    - log: "Only callable from another flow."
```

## Variables

Process arguments, saved process state and
[automatically provided variables](#provided-variables) are exposed as flow
variables:

```yaml
flows:
  default:
    - log: "Hello, ${initiator.displayName}"
```

In the example above the expression `${initator.displayName}` references an
automatically provided variable `inititator` and retrieves it's `displayName`
field value.

Flow variables can be defined in multiple ways:
- using the DSL's [set step](./flows.md#setting-variables);
- the [arguments](./configuration.md#arguments) section in the process
configuration;
- passed in the API request when the process is created;
- produced by tasks;
- etc.

Variables can be accessed using expressions,
[scripts](../getting-started/scripting.md) or in
[tasks](./tasks.md).

```yaml
flows:
  default:
    - log: "All variables: ${allVariables()}"
    
    - if: ${hasVariable('var1')}
      then:
        - log: "Yep, we got 'var1' variable with value ${var1}"
      else:
        - log: "Nope, we do not have 'var1' variable"
        
    - script: javascript
      body: |
        var allVars = execution.variables().toMap();
        print('Getting all variables in a JavaScript snippet: ' + allVars);

        execution.variables().set('newVar', 'hello');
```

The `allVariables` function returns a Java Map object with all current
variables.

The `hasVariable` function accepts a variable name (as a string parameter) and
returns `true` if the variable exists.

### Provided Variables

Concord automatically provides several built-in variables upon process
execution in addition to the defined [variables](#variables):

- `txId` - an unique identifier of the current process;
- `parentInstanceId` - an identifier of the parent process;
- `workDir` - path to the working directory of a current process;
- `initiator` - information about the user who started a process:
  - `initiator.username` - login, string;
  - `initiator.displayName` - printable name, string;
  - `initiator.email` - email address, string;
  - `initiator.groups` - list of user's groups;
  - `initiator.attributes` - other LDAP attributes; for example
    `initiator.attributes.mail` contains the email address.
- `currentUser` - information about the current user. Has the same structure
  as `initiator`;
- `requestInfo` - additional request data (see the note below):
  - `requestInfo.query` - query parameters of a request made using user-facing
    endpoints (e.g. the portal API);
  - `requestInfo.ip` - client IP address, where from request is generated.
  - `requestInfo.headers` - headers of request made using user-facing endpoints.
- `projectInfo` - project's data:
  - `projectInfo.orgId` - the ID of the project's organization;
  - `projectInfo.orgName` - the name of the project's organization;
  - `projectInfo.projectId` - the project's ID;
  - `projectInfo.projectName` - the project's name;
  - `projectInfo.repoId` - the project's repository ID;
  - `projectInfo.repoName` - the repository's name;
  - `projectInfo.repoUrl` - the repository's URL;
  - `projectInfo.repoBranch` - the repository's branch;
  - `projectInfo.repoPath` - the repository's path (if configured);
  - `projectInfo.repoCommitId` - the repository's last commit ID;
  - `projectInfo.repoCommitAuthor` - the repository's last commit author;
  - `projectInfo.repoCommitMessage` - the repository's last commit message.
- `processInfo` - the current process' information:
  - `processInfo.activeProfiles` - list of active profiles used for the current
  execution;
  - `processInfo.sessionToken` - the current process'
  [session token](../getting-started/security.md#using-session-tokens) can be
  used to call Concord API from flows.

LDAP attributes must be allowed in [the configuration](./configuration.md#server-configuration-file).

**Note:** only the processes started using [the browser link](../api/process.md#browser)
provide the `requestInfo` variable. In other cases (e.g. processes
[triggered by GitHub](../triggers/github.md)) the variable might be undefined
or empty. 

Availability of other variables and "beans" depends on the installed Concord
plugins, the arguments passed in at the process invocation, and stored in the
request data.

### Output Variables

Concord has the ability to return process data when a process completes.
The names of returned variables should be declared in the `configuration` section:

```yaml
configuration:
  out:
    - myVar1
```

Output variables may also be declared dynamically using `multipart/form-data`
parameters if allowed in a Project's configuration. **CAUTION: this is a not
secure if secret values are stored in process variables**

```bash
$ curl ... -F out=myVar1 https://concord.example.com/api/v1/process
{
  "instanceId" : "5883b65c-7dc2-4d07-8b47-04ee059cc00b"
}
```

Retrieve the output variable value(s) after the process finishes:

```bash
# wait for completion...
$ curl .. https://concord.example.com/api/v2/process/5883b65c-7dc2-4d07-8b47-04ee059cc00b
{
  "instanceId" : "5883b65c-7dc2-4d07-8b47-04ee059cc00b",
  "meta": {
    out" : {
      "myVar1" : "my value"
    },
  }  
}
```

It is also possible to retrieve a nested value:

```yaml
configuration:
  out:
    - a.b.c

flows:
  default:
    - set:
        a:
          b:
            c: "my value"
            d: "ignored"
```

```bash
$ curl ... -F out=a.b.c https://concord.example.com/api/v1/process
```

In this example, Concord looks for variable `a`, its field `b` and
the nested field `c`.

Additionally, the output variables can be retrieved as a JSON file:

```bash
$ curl ... https://concord.example.com/api/v1/process/5883b65c-7dc2-4d07-8b47-04ee059cc00b/attachment/out.json

{"myVar1":"my value"}
```

Any value type that can be represented as JSON is supported.

### Dry-run mode

The dry-run mode allows you to execute a process without making any dangerous side-effects.
This is useful for testing and validating the flow logic before running it in production. 

> Note that correctness of the flow execution in dry-run mode depend on how tasks and scripts
> handle dry-run mode in your flow. Make sure all tasks and scripts involved are properly handled
> dry-run mode to prevent unintended side effects

#### Running a Flow in Dry-Run Mode

To enable dry-run mode, set the `dryRun` flag to `true` in the process request:

```bash
curl ... -FdryRun=true -F out=myVar1 https://concord.example.com/api/v1/process
```

When the process is launched in dry-run mode, the `system` log segment of the process will include
the following line:

```
Dry-run mode: enabled
```

### Behavior of Tasks and Scripts in Dry-Run mode

#### Tasks

Standard Concord tasks support dry-run mode and will not make any changes outside the process.
For example, the `http` task will not make any non-GET requests in dry-run mode, the `s3` task will
not actually upload files in dry-run mode, etc.

If a task does not support dry-run mode, the process will terminate with the following error:

```
Dry-run mode is not supported for '<task-name>' task (yet)
```

If a task does not support dry-run mode, but you are confident that it can be executed in dry-run
mode, you can mark the task step as ready for dry-run mode:

```yaml
flows:
  myFlow:
    - task: "myTaskAndImSureWeCanExecuteItInDryRunMode"
      meta:
        dryRunReady: true   # dry-run ready marker for this step
```

> **Important**: Use the `meta.dryRunReady` only if you are certain that the task is safe to run in
> dry-run mode and cannot be modified to support it explicitly.

To add dry-run mode support to a task, see the [task documentation](../processes-v2/tasks.md#dry-run-mode).

#### Scripts

By default, script steps do not support dry-run mode and the process will terminate with
the following error:

```
Dry-run mode is not supported for this 'script' step
```

To add dry-run mode support to a script, see
the [scripting documentation](../getting-started/scripting.md#dry-run-mode).
