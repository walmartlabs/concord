# Configuration

The `configuration` sections contains [dependencies](#dependencies),
[arguments](#arguments) and other process configuration values.

- [Merge Rules](#merge-rules)
- [Entry Point](#entry-point)
- [Arguments](#arguments)
- [Dependencies](#dependencies)
- [Requirements](#requirements)
- [Process Timeout](#process-timeout)
  - [Running Timeout](#running-timeout)
  - [Suspend Timeout](#suspend-timeout)
- [Exclusive Execution](#exclusive-execution)
- [Metadata](#metadata)
- [Template](#template)
- [Runner](#runner)
- [Debug](#debug)

## Merge Rules

Process `configuration` values can come from different sources: the section in
the `concord.yml` file, request parameters, policies, etc. Here's the order in
which all `configuration` sources are merged before the process starts:

- environment-specific [default values](../getting-started/configuration.md#default-process-variables);
- [defaultCfg](../getting-started/policies.md#default-process-configuration-rule) policy values;
- the current organization's configuration values;
- the current [project's configuration](../api/project.md#get-project-configuration) values;
- values from current active [profiles](./profiles.md)
- configuration file send in [the process start request](../api/process.md#start);
- [processCfg](../getting-started/policies.md#process-configuration-rule) policy values.

## Entry Point

The `entryPoint` configuration sets the name of the flow that will be used for
process executions. If no `entryPoint` is specified the flow labelled `default`
is used automatically, if it exists.

```yaml
configuration:
  entryPoint: "main"
flows:
  main:
  - log: "Hello World"
```

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

The variables are evaluated in the order of definition. For example, it is
possible to use a variable value in another variable if the former is defined
earlier than the latter:

```yaml
configuration:
  arguments:
    name: "Concord"
    message: "Hello, ${name}"
```

A variable's value can be [defined or modified with the set step](./flows.md#setting-variables) and a
[number of variables](./index.md#provided-variables) are automatically set in
each process and available for usage.

## Dependencies

The `dependencies` array allows users to specify the URLs of dependencies such
as:

- Concord plugins and their dependencies
- Dependencies needed for specific scripting language support
- Other dependencies required for process execution

```yaml
configuration:
  dependencies:
  # maven URLs...
  - mvn://org.codehaus.groovy:groovy-all:2.4.12
  # or direct URLs
  - https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.4.12/groovy-all-2.4.12.jar"
  - https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.6/commons-lang3-3.6.jar"
```

The artifacts are downloaded and added to the classpath for process execution
and are typically used for [task implementations](../getting-started/tasks.md).

Multiple versions of the same artifact are replaced with a single one, following
 standard Maven resolution rules.

Usage of the `mvn:` URL pattern is preferred since it uses the centrally
configured [list of repositories](../getting-started/configuration.md#dependencies)
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
  - "mvn://com.walmartlabs.concord:concord-client:{{ site.concord_core_version }}?transitive=false"
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
Concord plugins.

## Requirements

A process can have a specific set of `requirements` configured. Requirements
are used to control where the process is executed and what kind of resources it
requires.

The server uses `requirements.agent` value to determine which agents it can set
the process to. For example, if the process specifies

```yaml
configuration:
  requirements:
    agent:
      favorite: true
``` 

and there is an agent with

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

Custom `jvm` arguments can be specified in the `requirements` section of the
`configuration` object. [Concord Agent](../getting-started/index.md#concord-agent)
pass these arguments to the process' JVM:

```yaml
configuration:
  requirements:
    jvm:
      extraArgs:
        - "-Xms256m"
        - "-Xmx512m"
```

**Note:** Processes with custom `jvm` arguments can't use the "pre-fork"
mechanism and are usually slower to start.

**Note:** Consult with your Concord instance's admin to determine what the limitations
are for JVM memory and other settings. 

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
    - "${sleep.ms(60000)}" # simulate a long-running task
```

In the example above, if another process in the same project with the same
`group` value is submitted, it will be immediately cancelled.

If `mode` set to `wait` then only one process in the same `group` is allowed to
run.

**Note:** this feature available only for project processes. 

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
$ curl -skn http://concord.example.com/api/v1/process/1c50ab2c-734a-4b64-9dc4-fcd14637e36c | jq '.meta.myValue'
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
$ curl -skn http://concord.example.com/api/v1/process/1c50ab2c-734a-4b64-9dc4-fcd14637e36c | jq '.meta.["nested.value"]'
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

## Template

A template can be used to allow inheritance of all the configurations of another
project. The value for the `template` field has to be a valid URL pointing to
a JAR-archive of the project to use as template.

The template is downloaded for [process execution](./index.md)
and exploded in the workspace. More detailed documentation, including
information about available templates, can be found in the
[templates section](../templates/index.md).

## Runner

[Concord Runner]({{ site.concord_source }}tree/master/runtime/v1/impl) is
the name of the default runtime used for actual execution of processes. Its
parameters can be configured in the `runner` section of the `configuration`
object. Here is an example of the default configuration:

```yaml
configuration:
  runner:
    debug: false
    logLevel: "INFO"
    events:
      recordTaskInVars: false
      inVarsBlacklist:
        - "password"
        - "apiToken"
        - "apiKey"

      recordTaskOutVars: false
      outVarsBlacklist: []
```

- `debug` - enables additional debug logging, `true` if `configuration.debug`
  enabled;
- `logLevel` - [logging level](https://logback.qos.ch/manual/architecture.html#effectiveLevel)
  for the `log` task;
- `events` - the process event recording parameters:
  - `recordTaskInVars` - enable or disable recording of input variables in task
    calls;
  - `inVarsBlacklist` - list of variable names that must not be recorded if
    `recordTaskInVars` is `true`;
  - `recordTaskOutVars` - enable or disable recording of output variables in
    task calls;
  - `outVarsBlacklist` - list of variable names that must not be recorded if
    `recordTaskInVars` is `true`.

See the [Process Events](../getting-started/processes.md#process-events)
section for more details about the process event recording.

## Debug

Enabling the `debug` configuration option causes Concord to log paths of all
resolved dependencies. It is useful for debugging classpath conflict issues:

```yaml
configuration:
  debug: true
```
