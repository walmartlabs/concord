# Configuration

The `configuration` sections contains [dependencies](#dependencies),
[arguments](#arguments) and other process configuration values.

- [Merge Rules](#merge-rules)
- [Runtime](#runtime)
- [Entry Point](#entry-point)
- [Arguments](#arguments)
- [Dependencies](#dependencies)
- [Requirements](#requirements)
- [Process Timeout](#process-timeout)
  - [Running Timeout](#running-timeout)
  - [Suspend Timeout](#suspend-timeout)
- [Exclusive Execution](#exclusive-execution)
- [Metadata](#metadata)
- [Events](#events)

## Merge Rules

Process `configuration` values can come from different sources: the section in
the `concord.yml` file, request parameters, policies, etc. Here's the order in
which all `configuration` sources are merged before the process starts:

- environment-specific [default values](./configuration.md#default-process-variables);
- [defaultCfg](../getting-started/policies.md#default-process-configuration-rule) policy values;
- the current organization's configuration values;
- the current [project's configuration](../api/project.md#get-project-configuration) values;
- values from current active [profiles](./profiles.md)
- configuration file send in [the process start request](../api/process.md#start);
- [processCfg](../getting-started/policies.md#process-configuration-rule) policy values.

## Runtime

The `runtime` parameter can be used to specify the execution runtime:

```yaml
configuration:
  runtime: "concord-v2"
```

Currently, the default `runtime` is `concord-v1` which is considered stable and
production-ready. It will remain available for the foreseeable future, but will
see fewer (if any) feature updates. This section describes the new and improved
`concord-v2` runtime. There are breaking changes to syntax and execution semantics
from `concord-v1` which require [migration](./migration.md) considerations.

See the [Processes (v1)](../processes-v1/index.md) section for more details
about `concord-v1` runtime.

## Entry Point

The `entryPoint` configuration sets the name of the flow that will be used for
process executions. If no `entryPoint` is specified the flow labelled `default`
is used automatically, if it exists.

```yaml
configuration:
  entryPoint: "main" # use "main" instead of "default"

flows:
  main:
    - log: "Hello World"
```

**Note:** some flow names have special meaning, such as `onFailure`, `onCancel`
and `onTimeout`. See the [error handling](./flows.md#error-handling) section
for more details.

## Arguments

Default values for arguments can be defined in the `arguments` section of the
configuration as simple key/value pairs as well as nested values:

```yaml
configuration:
  arguments:
    name: "Example"
    coordinates:
      x: 10
      y: 5
      z: 0

flows:
  default:
    - log: "Project name: ${name}"
    - log: "Coordinates (x,y,z): ${coordinates.x}, ${coordinates.y}, ${coordinates.z}"
```

Values of `arguments` can contain [expressions](./flows.md#expressions). Expressions can
use all regular tasks:

```yaml
configuration:
  arguments:
    listOfStuff: ${myServiceTask.retrieveListOfStuff()}
    myStaticVar: 123
```

Concord evaluates arguments in the order of definition. For example, it is
possible to use a variable value in another variable if the former is defined
earlier than the latter:

```yaml
configuration:
  arguments:
    name: "Concord"
    message: "Hello, ${name}"
```

A variable's value can be [defined or modified with the set step](./flows.md#setting-variables)
and a [number of variables](./index.md#provided-variables) are automatically
set in each process and available for usage.

## Dependencies

The `dependencies` array allows users to specify the URLs of dependencies such
as:

- plugins ([tasks](./tasks.md)) and their dependencies;
- dependencies needed for specific scripting language support;
- other dependencies required for process execution.

```yaml
configuration:
  dependencies:
    # maven URLs...
    - "mvn://org.codehaus.groovy:groovy-all:2.4.12"
    # or direct URLs
    - "https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.4.12/groovy-all-2.4.12.jar"
    - "https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.6/commons-lang3-3.6.jar"
```

Concord downloads the artifacts and adds them to the process' classpath.

Multiple versions of the same artifact are replaced with a single one,
following standard Maven resolution rules.

Usage of the `mvn:` URL pattern is preferred since it uses the centrally
configured [list of repositories](./configuration.md#dependencies)
and downloads not only the specified dependency itself, but also any required
transitive dependencies. This makes the Concord project independent of access
to a specific repository URL, and hence more portable.

Maven URLs provide additional options:

- `transitive=true|false` - include all transitive dependencies
  (default `true`);
- `scope=compile|provided|system|runtime|test` - use the specific
  dependency scope (default `compile`).

Additional options can be added as "query parameters" parameters to
the dependency's URL:
```yaml
configuration:
  dependencies:
    - "mvn://com.walmartlabs.concord:concord-client:{{site.concord_core_version}}?transitive=false"
```

The syntax for the Maven URL uses the groupId, artifactId, optionally packaging,
and version values - the GAV coordinates of a project. For example the Maven
`pom.xml` for the Groovy scripting language runtime has the following
definition:

```xml
<project>
  <groupId>org.codehaus.groovy</groupId>
  <artifactId>groovy-all</artifactId>
  <version>2.4.12</version>
  ...
</project>
```

This results in the path
`org/codehaus/groovy/groovy-all/2.4.12/groovy-all-2.4.12.jar` in the
Central Repository and any repository manager proxying the repository.

The `mvn` syntax uses the short form for GAV coordinates
`groupId:artifactId:version`, so for example
`org.codehaus.groovy:groovy-all:2.4.12` for Groovy.

Newer versions of groovy-all use `<packaging>pom</packaging>` and define
dependencies. To use a project that applies this approach, called Bill of
Material (BOM), as a dependency you need to specify the packaging in between
the artifactId and version. For example, version `2.5.21` has to be specified as
`org.codehaus.groovy:groovy-all:pom:2.5.21`:

```yaml
configuration:
  dependencies:
  - "mvn://org.codehaus.groovy:groovy-all:pom:2.5.21"
```

The same logic and syntax usage applies to all other dependencies including
Concord [plugins]({{ site.concord_plugins_v2_docs }}/index.md).

## Requirements

A process can have a specific set of `requirements` configured. Concord uses
requirements to control where the process should be executed and what kind of
resources it gets. For example, if the process specifies

```yaml
configuration:
  requirements:
    agent:
      favorite: true
``` 

and if there is an agent with

```
concord-agent {
  capabilities = {
    favorite = true
  }
}
```

in its configuration file then it is a suitable agent for the process.

Following rules are used when matching `requirements.agent` values of processes
and agent `capabilities`:
- if the value is present in `capabilities` but missing in `requirements.agent`
is is **ignored**;
- if the value is missing in `capabilities` but present in `requirements.agent`
then it is **not a match**;
- string values in `requirements.agent` are treated as **regular expressions**,
i.e. in pseudo code `capabilities_value.regex_match(requirements_value)`;
- lists in `requirements.agent` are treated as "one or more" match, i.e. if one
or more elements in the list must match the value from `capabilities`;
- other values are compared directly.

More examples:

```yaml
configuration:
  requirements:
    agent:
      size: ".*xl"
      flavor:
        - "vanilla"
        - "chocolate"
```

matches agents with:

```
concord-agent {
  capabilities = {
    size = "xxl"
    flavor = "vanilla"
  }
}
```

### Process Timeout

You can specify the maximum amount of time that a process can be in a some state. 
After this timeout process automatically canceled and marked as `TIMED_OUT`.  

Currently, the runtime provides two different timeout parameters:
- [processTimeout](#running-timeout) - how long the process can stay in
  the `RUNNING` state;
- [suspendTimeout](#suspend-timeout) - how long the process can stay in
  the `SUSPENDED` state. 

Both timeout parameters accepts duration in the
[ISO 8601](https://en.wikipedia.org/wiki/ISO_8601) format:

```yaml
configuration:
  processTimeout: "PT1H" # 1 hour
```

A special `onTimeout` flow can be used to handle timeouts:

```yaml
flows:
  onTimeout:
  - log: "I'm going to run when my parent process times out"
```

The way Concord handles timeouts is described in more details in
the [error handling](./flows.md#handling-cancellations-failures-and-timeouts)
section.

#### Running Timeout

You can specify the maximum amount of time the process can spend in
the `RUNNING` state with the `processTimeout` configuration. It can be useful
to set specific SLAs for deployment jobs or to use it as a global timeout:

```yaml
configuration:
  processTimeout: "PT1H"
flows:
  default:
    # a long running process
```

In the example above, if the process runs for more than 1 hour it is
automatically cancelled and marked as `TIMED_OUT`.

**Note:** forms waiting for input and other processes in `SUSPENDED` state
are not affected by the process timeout. I.e. a `SUSPENDED` process can stay
`SUSPENDED` indefinitely -- up to the allowed data retention period.

#### Suspend Timeout

You can specify the maximum amount of time the process can spend in
the `SUSPEND` state with the `suspendTimeout` configuration. It can be useful
to set specific SLAs for forms waiting for input and processes waiting for
external events:

```yaml
configuration:
  suspendTimeout: "PT1H"
flows:
  default:
    - task: concord
      in:
       action: start
       org: myOrg
       project: myProject
       repo: myRepo
       sync: true
       suspend: true
  ...
```

In the example above, if the process waits for more than 1 hour it is
automatically cancelled and marked as `TIMED_OUT`.

## Exclusive Execution

The `exclusive` section in the process `configuration` can be used to configure
exclusive execution of the process:

```yaml
configuration:
  exclusive:
    group: "myGroup"
    mode: "cancel"

flows:
  default:
    - ${sleep.ms(60000)} # simulate a long-running task
```

In the example above, if another process in the same project with the same
`group` value is submitted, it will be immediately cancelled.

If `mode` set to `wait` then only one process in the same `group` is allowed to
run.

**Note:** this feature available only for processes running in a project.

See also: [Exclusive Triggers](../triggers/index.md#exclusive-triggers).

## Metadata

Flows can expose internal variables as process metadata. Such metadata can be
retrieved using the [API](../api/process.md#status) or displayed in
the process list in [Concord Console](../console/process.md#process-metadata).

```yaml
configuration:
  meta:
    myValue: "n/a" # initial value

flows:
  default:
    - set:
        myValue: "hello!"
```

After each step, Concord sends the updated value back to the server:

```bash
$ curl -skn http://concord.example.com/api/v1/process/1c50ab2c-734a-4b64-9dc4-fcd14637e36c |
    jq '.meta.myValue'

"hello!"
```

Nested variables and forms are also supported:

```yaml
configuration:
  meta:
    nested.value: "n/a"

flows:
  default:
    - set:
        nested:
          value: "hello!"
```

The value is stored under the `nested.value` key:

```bash
$ curl -skn http://concord.example.com/api/v1/process/1c50ab2c-734a-4b64-9dc4-fcd14637e36c |
    jq '.meta.["nested.value"]'

"hello!"
```

Example with a form:

```yaml
configuration:
  meta:
    myForm.myValue: "n/a"

flows:
  default:
    - form: myForm
      fields:
      - myValue: { type: "string" }
```

## Events

The [process event recording](../getting-started/processes.md#process-events)
can be configured using the `events` section. Here is an example of the default
configuration:

```yaml
configuration:
  events:
    recordTaskInVars: false
    truncateInVars: true
    recordTaskOutVars: false
    truncateOutVars: true
    truncateMaxStringLength: 1024
    truncateMaxArrayLength: 32
    truncateMaxDepth: 10
    inVarsBlacklist:
      - "apiKey"
      - "apiToken"
      - "password"
      - "privateKey"
      - "vaultPassword"
    outVarsBlacklist: []
```

- `recordTaskInVars`, `recordTaskOutVars` - enable or disable recording of
input/output variables in task calls;
- `truncateInVars`, `truncateOutVars` - if `true` the runtime truncates
the recorded values to prevent spilling large values into process events;
- `inVarsBlacklist`, `outVarsBlacklist` - list of variable names that must
not be included recorded;
- `truncateMaxStringLength` - maximum allowed length of string values.
The runtime truncates strings larger than the specified value;
- `truncateMaxArrayLength` - maximum allowed length of array (list) values;
- `truncateMaxDepth` - maximum allowed depth of nested data structures (e.g.
nested `Map` objects).

**Note:** in the [runtime v1](../processes-v1/configuration.md#runner)
the event recording configuration was a subsection of the `runner` section.
In the runtime v2 it is a direct subsection of the `configuration` block.
