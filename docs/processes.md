# Processes

## Payload format

The server expects a ZIP archive of the following structure:
- `.concord.yml` - main project file;
- `_main.json` - request data in JSON format (see [below](#request-data));
- `processes` - directory containing `.yml` process and form definitions;
- `lib` - directory for additional runtime dependencies.

Anything else will be unpacked as-is and will be available for a process.
The plugins can require other files to be present in a payload.

## Project file

A payload archive can contain a project file: `.concord.yml`.
This file will be loaded first and can contain process and flow definitions,
input variables and profiles:

```yaml
flows:
  main:
  - form: myForm
  - log: Hello, ${myForm.name}
  
forms:
  myForm:
  - name: {type: "string"}
  
variables:
  dependencies: ["..."]
  otherCfgVar: 123
  arguments:
    myForm: {name: "stranger"}
    
profiles:
  myProfile:
    variables:
      arguments:
        myAlias: "stranger"
        myForm: {name: "${myAlias}"}
```

Profiles can override default variables, flows and forms. For example, if the
process above will be executed using `myProfile` profile, then the default
value of `myForm.name` will be `world`.

See also [the YAML format for describing flows and forms](./yaml.md).

## Request data

A payload's `_main.json` file is either supplied by users or created by the
server from an user's request data.

The request's JSON format:
```json
{
  "entryPoint": "...",
  "activeProfiles": ["myProfile", "..."],
  "otherCfgVar": 123,
  "arguments": {
    "myForm": {
      "name": "John"
    }
  }
}
```

Only `entryPoint` parameter is mandatory. The `activeProfiles` parameter is a
list of project file's profiles that will be used to start a process. If not
set, a `default` profile will be used.

## Variables

Before executing a process, variables from a project file and a request data
are merged. Project variables override default project variables and then
user request's variables are applied.

There are a few variables that affect execution of a process:
- `template` - the name of a template, will be used by the server to create a
payload archive;
- `dependencies` - array of URLs, list of external JAR dependencies;
- `arguments` - a JSON object, will be used as process arguments.

Values of `arguments` can contain [expressions](./yaml.md#expressions).
Expressions can use all regular "tasks" plus external `dependencies`:

```yaml
variables:
  arguments:
    listOfStuff: ${myServiceTask.retrieveListOfStuff()}
    myStaticVar: 123
```

The order of evaluation is not guaranteed.

The `dependencies` array allows to pull external dependencies necessary
for the process' execution. Each element of the array must be a valid URL.
Dependencies are resolved by the agent, before starting a process.

## Provided variables

Concord automatically provides a few built-in variables:
- `execution` - a reference to a context variables map of a current
execution;
- `txId` - unique identifier of a current execution;
- `tasks` - allows access to available tasks (for example:
  `${tasks.get('oneops')}`);
- `initiator` - information about user who started a process:
  - `initiator.username` - login, string;
  - `initiator.displayName` - printable name, string;
  - `initiator.groups` - list of user's groups;
  - `initiator.attributes` - other LDAP attributes.

LDAP attributes must be whitelisted in [the configuration](./configuration.md#ldap).

Availability of other variables and "beans" depends on installed
Concord's plugins and arguments passed on a process' start.
See also the document on
[how to create custom tasks](./extensions.md#tasks).


## Starting a new process instance

*TBD*

See also: the [process](./api/process.md) API endpoint.
