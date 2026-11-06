# Templates

> Consider a simpler mechanism of [Imports](../processes-v1/imports.md)
first.

Templates allow users to share common elements between different
projects and processes.

Templates can contain the same type of files which are used in a
regular [process payload](../getting-started/processes.md), plus additional
instructions on how to modify process' data.

Process files will overwrite any template files with the same name,
this way a user can "override" any resource provided by a template.

Additionally, using [template aliases](#usage), it is possible to
create Concord flows which don't require sending a payload archive
or having a GIT repository with flows and can be called with a
simple HTTP request.

## Creating a template

Template is a regular JAR or ZIP archive with the structure similar
to a regular process payload.

For example, the
[ansible template]({{site.concord_source}}tree/master/plugins/templates/ansible/src/main/filtered-resources)
has the following structure:

```
_callbacks/  # (1)
  trace.py

processes/   # (2)
  main.yml

_main.js     # (3)
```

It contains additional resources needed for the ansible task (1),
a folder with a flow definition (2) and a pre-processing script (3).

Template archive must be uploaded to a repository just like a regular
artifact.

## Pre-processing

If a template contains `_main.js` file, it will be executed by the
server before starting a process. The script must return a JSON
object which will be used as process variables.

For example, if `_main.js` looks like this:

```javascript
({
    entryPoint: "main",
    arguments: {
      message: _input.message,
      name: "Concord"        
    }
})
```

then given this request data

```json
{
  "message": "Hello,"
}
```

the process variables will look like this:

```json
{
  "entryPoint": "main",
  "arguments": {
    "message": "Hello,",
    "name": "Concord"
  }
}
```

A special `_input` variable is provided to access source data from a
template script.

## Usage

Template can be referenced with a `template` entry in process variables:

```yaml
flows:
  default:
  - log: "${message} ${name}"

configuration:
  template: "http://host/path/my-template.jar"
  # or by using a maven repository 
  template: "mvn://groupId:artifactId:version"
```

Only one template can be used at a time.

The `template` parameter can also be specified in request JSON data
or profiles.

Templates also can be references by their aliases:

```yaml
configuration:
  template: "my-template"
```

The alias must be added using the [template API](../api/template.md).
