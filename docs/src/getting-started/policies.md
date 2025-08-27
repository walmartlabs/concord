# Policies

Policies is a powerful and flexible mechanism to control different
characteristics of processes and system entities.

- [Overview](#overview)
- [Document Format](#document-format)
- [Attachment Rule](#attachment-rule)
- [Ansible Rule](#ansible-rule)
- [Dependency Rule](#dependency-rule)
- [Dependency Rewrite Rule](#dependency-rewrite-rule)
- [Dependency Versions Rule](#dependency-versions-rule)
- [Entity Rule](#entity-rule)
- [File Rule](#file-rule)
- [JSON Store Rule](#json-store-rule)
- [Process Configuration Rule](#process-configuration-rule)
- [Default Process Configuration Rule](#default-process-configuration-rule)
- [Task Rule](#task-rule)
- [Workspace Rule](#workspace-rule)
- [Runtime Rule](#runtime-rule)
- [RawPayload Rule](#rawpayload-rule)
- [CronTrigger Rule](#crontrigger-rule)

## Overview

A policy is a JSON document describing rules that can affect the execution of
processes, creation of entities such as project and secrets, define the limits
for the process queue, etc.

Policies can be applied system-wide as well as linked to an organization, a
specific project or to a user.

Policies can be created using the [Policy API](../api/policy.md). Currently,
only the users with the administrator role can create or link policies.

Policies can inherent other policies - in this case the parent policies are
applied first, going from the \"oldest\" ancestors to the latest link.

## Document Format

There are two types of objects in the policy document: `allow/deny/warn` actions
and free-form group of attributes:

```json
{
  "[actionRules]": {
    "deny": [
      {
        ...rule...
      }
    ],
    "warn": [
      {
        ...rule...
      }
    ],
    "allow": [
      {
        ...rule...
      }
    ]
  },
  
  "[anotherRule]": {
    ...rule...
  }
}
```

Here's the list of currently supported rules:
- [ansible](#ansible-rule) - controls the execution of
  [Ansible]({{ site.concord_plugins_v2_docs }}/ansible.md) plays;
- [dependency](#dependency-rule) - applies rules to process dependencies;
- [entity](#entity-rule) - controls creation or update of entities
  such as organizations, projects and secrets;
- [file](#file-rule) - applies to process files;
- [processCfg](#process-configuration-rule) - allows changing the process'
  `configuration` values;
- [queue](#queue-rule) - controls the process queue behaviour;
- [task](#task-rule) - applies rules to flow tasks;
- [workspace](#workspace-rule) - controls the size of the workspace.

## Attachment Rule

Attachment rules allow you to limit the size of
[process attachments](../api/process.md#downloading-an-attachment).

The syntax:

```json
{
   "attachments": {
      "msg": "The size of process attachments exceeds the allowed value: current {0} byte(s), limit {1} byte(s)",
      "maxSizeInBytes": 1024
   }
}
```

Concord applies the limit to all files stored in the process'
`${workDir}/_attachments` directory, including the process state
files (variables, flow state, etc) and all files created during
the execution of the process.

## Ansible Rule

Ansible rules allow you to control the execution of
[Ansible]({{ site.concord_plugins_v2_docs }}/ansible.md) plays.

The syntax:

```json
{
  "action": "ansibleTaskName",
  "params": [
    {
      "name": "paramName",
      "values": ["arrayOfValues"]
    }
  ],
  "msg": "optional message"
}
```

The `action` attribute defines the name of the Ansible step and the `params`
object is matched with the step's input parameters. The error message can be
specified using the `msg` attribute.

For example, to forbid a certain URI from being used in the Ansible's
[get_url](https://docs.ansible.com/ansible/2.6/modules/get_url_module.html)
step:

```json
{
  "ansible": {
    "deny": [
      {
        "action": "get_url",
        "params": [
          {
            "name": "url",
            "values": ["https://jsonplaceholder.typicode.com/todos"]
          }
        ],
        "msg": "Found a forbidden URL"
      }
    ]
  }
}
```

If someone tries to use the forbidden URL in their `get_url`, they see a
message in the process log:

```
ANSIBLE:  [ERROR]: Task 'get_url (get_url)' is forbidden by the task policy: Found a
ANSIBLE: forbidden URL
```

The Ansible rule supports [regular JUEL expressions](../processes-v1/flows.md#expressions)
which are evaluated each time the Ansible plugin starts using the current
process' context. This allows users to create context-aware Ansible policies:

```json
{
  "ansible": {
    "deny": [
      {
        "action": "maven_artifact",
        "params": [
          {
            "artifact_url": "url",
            "values": ["${mySecretTask.getForbiddenArtifacts()}"]
          }
        ]
      }
    ]
  }
}
```

**Note:** the `artifact_url` from the example above is not a standard
[maven_artifact](https://docs.ansible.com/ansible/2.6/modules/maven_artifact_module.html)
step's parameter. It is created dynamically from the supplied values of
`repository_url`, `group_id`, `artifact_id`, etc.

## Dependency Rule

Dependency rules provide a way to control which process dependencies are allowed
for use.

The syntax:

```json
{
  "scheme": "...scheme...",
  "groupId": "...groupId...",
  "artifactId": "...artifactId...",
  "fromVersion": "1.0.0",
  "toVersion": "1.1.0",
  "msg": "optional message"
}
```

The attributes:

- `scheme` - the dependency URL scheme. For example: `http` or `mvn`;
- `groupId` and `artifactId` - parts of the dependency's Maven GAV (only for
`mvn` dependencies);
- `fromVersion` and `toVersion` - define the version range (only for `mvn`
dependencies).

For example, restricting a specific version range of a plugin can be done like
so:

```json
{
  "dependency": {
    "deny": [
      {
        "groupId": "com.walmartlabs.concord.plugins.basic",
        "artifactId": "ansible-tasks",
        "toVersion": "1.13.1",
        "msg": "Usage of ansible-tasks <= 1.14.0 is forbidden"
      }
    ]
  }
}
```

In this example, all versions of the `ansible-tasks` dependency lower than
`1.13.1` are rejected.

Another example, warn users every time they are trying to use non-`mvn`
dependencies:

```json
{
"dependency": {
    "warn": [
      {
        "msg": "Using direct dependency URLs is not recommended. Consider using mvn:// dependencies.",
        "scheme": "^(?!mvn.*$).*"
      }
    ]
  }
}
```

## Dependency Rewrite Rule

Dependency rewrite rules provide a way to change dependency artifacts (e.g. dependency versions).

The syntax:

```json
{
  "msg": "optional message",
  "groupId": "...groupId...",
  "artifactId": "...artifactId...",
  "fromVersion":"...optional lower bound (inclusive) of version...",
  "toVersion":"..optional upper bound (inclusive) of version...",
  "value":"mvn://new dependency artifact"  
}
```

The attributes:

- `groupId` and `artifactId` - parts of the dependency's Maven GAV;
- `fromVersion` and `toVersion` - define the version range;
- `value` - new dependency value.

For example, updating groovy dependency version to `2.5.21`:

```json
{
  "dependencyRewrite": [
      {
        "groupId": "org.codehaus.groovy",
        "artifactId": "groovy-all",
        "toVersion": "2.5.20",
        "value": "mvn://org.codehaus.groovy:groovy-all:pom:2.5.21"
      }
  ]
}
```

## Dependency Versions Rule

The dependency versions rule provides a way to map `latest` version tags of
[process dependencies](../processes-v1/configuration.md#dependencies) to
actual version values.

The syntax:

```json
[ 
    { 
     "artifact": "...groupId:artifactId...",
     "version": "...version"
    },

    { 
         "artifact": "...groupId:artifactId...",
         "version": "...version"
        },
    ...
]
```

The attributes:
- `artifact` - Maven's `groupId` and `artifactId` values, separated by colon `:`;
- `version` - the artifact's version to use instead of the `latest` tag.

For example:

```json
{
    "dependencyVersions": [ 
        { 
         "artifact": "com.walmartlabs.concord.plugins.basic:ansible-tasks",
         "version": "{{ site.concord_core_version }}"
        },
    
        { 
         "artifact": "mvn://com.walmartlabs.concord.plugins:jenkins-task",
         "version": "{{ site.concord_plugins_version }}"
        }
    ]
}
```

If a process specifies `latest` instead of the version:

```yaml
configuration:
  dependencies:
    - "mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:latest"
    - "mvn://com.walmartlabs.concord.plugins:jenkins-task:latest"
```

the effective dependency list is:

```yaml
- "mvn://com.walmartlabs.concord.plugins.basic:ansible-tasks:{{ site.concord_core_version }}"
- "mvn://com.walmartlabs.concord.plugins:jenkins-task:{{ site.concord_plugins_version }}"
```

## Entity Rule

Entity rules control the creation or update of Concord
[organizations](../api/org.md), [projects](../api/project.md),
[secrets](../api/secret.md), etc.

The syntax:

```json
{
  "entity": "entityType",
  "action": "action",
  "conditions": {
    "param": "value"
  },
  "msg": "optional message"
}
```

The currently supported `entity` types are:

- `org` - organizations;
- `project` - projects;
- `repository` - repositories in projects;
- `secret` - secrets;
- `jsonStore` - JSON stores;
- `jsonStoreItem` - items in JSON stores;
- `jsonStoreQuery` - JSON store queries;
- `trigger` - triggers.

Available actions:

- `create`
- `update`

The `conditions` are matched against an object containing both the entity's
and the entity's owner attributes:

```json
{
  "owner": {
    "id": "...userId...",
    "username": "...username...",
    "userType": "LOCAL or LDAP",
    "email": "...",
    "displayName": "...",
    "groups": ["AD/LDAP groups"],
    "attributes": {
      ...other AD/LDAP attributes...
    }  
  },
  "entity": {
    ...entity specific attributes...
  }
}
```

Different types of entities provide different sets of attributes:

- `org`:
  - `id` - organization ID (UUID, optional);
  - `name` - organization name;
  - `meta` - metadata (JSON object, optional);
  - `cfg` - configuration (JSON object, optional).
- `project`:
  - `id` - project ID (UUID, optional);
  - `name` - project name;
  - `orgId` - the project's organization ID (UUID);
  - `orgName` - the project's organization name;
  - `visibility` - the project's visibility (`PUBLIC` or `PRIVATE`);
  - `meta` - metadata (JSON object, optional);
  - `cfg` - configuration (JSON object, optional).
- `repository`:
  - `name` - repository name;
  - `url` - repository URL;
  - `branch` - branch name;
  - `secret` - reference to a secret (optional, JSON object, see below for
    the list of fields);
  - `orgId` - the project's organization ID (UUID);
  - `orgName` - the project's organization name;
  - `projectId` - project ID (UUID);
  - `projectName` - project name.
- `jsonStore`:
  - `name` - JSON store name;
  - `orgId` - the store's organization ID;
  - `visibility` - the store's visibility (optional);
  - `ownerId` - user ID of the store's owner (UUID, optional);
  - `ownerName` - username of the store's owner (optional);
  - `ownerDomain` - user domain of the store's owner (optional);
  - `ownerType` - user type of the store's owner (optional).
- `jsonStoreItem`:
  - `path` - item's path;
  - `data` - data (JSON object);
  - `jsonStoreId` - ID of the store (UUID);
  - `jsonStoreName` - name of the store;
  - `orgId` - the store's organization ID (UUID);
  - `orgName` - the store's organization name.
- `jsonStoreQuery`:
  - `name` - the query's name;
  - `text` - the query's text;
  - `storeId` - the store's ID (UUID);
  - `storeName` - the store's name;
  - `orgId` - the store's organization ID (UUID);
  - `orgName` - the store's organization name.
- `secret`:
  - `name` - project name;
  - `orgId` - the secrets's organization ID (UUID);
  - `type` - the secret's type;
  - `visibility` - the secret's visibility (`PUBLIC` or `PRIVATE`, optional);
  - `storeType` - the secret's store type (optional).
- `trigger`
  - `eventSource` - the trigger's event type (string, `github`, `manual`, etc);
  - `orgId` - linked organization's ID (UUID, optional);
  - `params` - the trigger's configuration (JSON object, optional).

For example, to restrict creation of projects in the `Default` organization use:

```json
{
   "entity": {
      "deny": [
         {
            "msg": "project in default org are disabled",
            "action": "create",
            "entity": "project",
            "conditions":{
               "entity": {
                  "orgId": "0fac1b18-d179-11e7-b3e7-d7df4543ed4f"
               }
            }
         }
      ]
   }
}
```

To prevent users with a specific AD/LDAP group from creating any new entities:

```json
{
   "entity": {
      "deny":[  
         {
            "action": ".*",
            "entity": ".*",
            "conditions": {
               "owner": {
               	  "userType": "LDAP",
               	  "groups": ["CN=SomeGroup,.*"]
               } 
            }
         }
      ]
   }
}
```

Another example is a policy to prevent users from creating wide-sweeping,
"blanket" GitHub triggers for all projects:

```json
{
    "entity": {
      "deny": [
         {
            "msg": "Blanket GitHub triggers are disallowed",
            "action": "create",
            "entity": "trigger",
            "conditions":{
               "entity": {
                  "eventSource": "github",
                  "params": {
                     "org": "\\.\\*",
                     "project": "\\.\\*",
                     "repository": "\\.\\*",
                     "unknownRepo": [true, false]
                  }
               }
            }
         }
      ]
   }
}
```

## File Rule

The file rules control the types and sizes of files that are allowed in
the process' workspace.

The syntax:

```json
{
  "maxSize": "1G",
  "type": "...type...",
  "names": ["...filename patterns..."],
  "msg": "optional message"
}
```

The attributes:

- `maxSize` - maximum size of a file (`G` for gigabytes, `M` - megabytes, etc);
- `type` - `file` or `dir`;
- `names` - filename patterns (regular expressions).

For example, to forbid files larger than 128Mb:

```json
{
  "file": {
    "deny": [
      {
        "maxSize": "128M",
        "msg": "Files larger than 128M are forbidden"
      }
    ]
  }
}
```

## JSON Store Rule

The `jsonStore` rule control parameters of [JSON stores](./json-store.md).

The syntax:
```json
{ 
  "data":{ 
     "maxSizeInBytes": 100,
     "msg": "optional message"
  },
  "store":{ 
     "maxNumberPerOrg": 30,
     "msg": "optional message"
  }
}
```

The attributes:

- `data`
  - `maxSizeInBytes` - maximum allowed size of a store in bytes;
- `store`
  - `maxNumberPerOrg` - maximum allowed number of stores per organization.

Example:

```json
{ 
   "jsonStore":{
      "data":{ 
         "maxSizeInBytes": 1048576
      },
      "store":{ 
         "maxNumberPerOrg": 30
      }
   }
}
```

## Process Configuration Rule

The `processCfg` values are merged into the process' `configuration` object,
overriding any existing values with the same keys:

```json
{
  "...variable...": "...value..."
}
```

Those values take precedence over the values specified by users in the process'
`configuration` section. The [defaultProcessCfg](#default-process-configuration-rule)
rule can be used to set the initial values. 

For example, to force a specific [processTimeout](../processes-v1/configuration.md#process-timeout)
value:

```json
{
  "processCfg": {
    "processTimeout": "PT2H"
  }
}
```

Or to override a value in `arguments`:

```json
{
  "processCfg": {
      "arguments": {
        "message": "Hello from Concord!"
      }
  }
}
```

## Default Process Configuration Rule

The `defaultProcessCfg` rule allows settings initial values for process
`configuration`. 

```json
{
  "...variable...": "...value..."
}
```

Those values can be overriden by users their process' `configuration` sections.
The [processCfg](#process-configuration-rule) rule can be used to override any
user values. 

For example, to set the default [processTimeout](../processes-v1/configuration.md#process-timeout)
value:

```json
{
  "defaultProcessCfg": {
    "processTimeout": "PT2H"
  }
}
```

## Queue Rule

The queue rule controls different aspects of the process queue - the maximum
number of concurrently running processes, the default process timeout, etc.

The syntax:

```json
{
  "concurrent": {
    "maxPerOrg": "10",
    "maxPerProject": "5",
    "msg": "optional message"
  },
  "forkDepth": {
    "max": 5,
    "msg": "optional message"
  },
  "processTimeout": {
    "max": "PT1H",
    "msg": "optional message"
  } 
}
```

The attributes:

- `concurrent` - controls the number of concurrently running processes:
  - `maxPerOrg` - max number of running processes per organization;
  - `maxPerProject` - max number of running processes per project;
- `forkDepth` - the maximum allowed depth of process forks, i.e. how many
_ancestors_ a process can have. Can be used to prevent "fork bombs";
- `processTimeout` - limits the maximum allowed value of the
[processTimeout parameter](../processes-v1/configuration.md#process-timeout).

For example:

```json
{
  "queue": {
    "forkDepth": {
      "max": 5
    },
    "concurrent": {
      "max": 40
    }
  }
}
```

## Task Rule

Task rules control the execution of flow tasks. They can trigger on specific
methods or parameter values.

The syntax:

```json
{
  "taskName": "...task name...",
  "method": "...method name...",
  "params": [
    {
      "name": "...parameter name...",
      "index": 0,
      "values": [
        false,
        null
      ],
      "protected": true
    }
  ],
  "msg": "optional message"
}
```

The attributes:

- `taskName` - name of the task (as in the task's `@Named` annotation);
- `method` - the task's method name;
- `params` - list of the task's parameters to match.

The `params` attribute accepts a list of parameter definitions:

- `name` - name of the parameter in the process' `Context`;
- `index` - index of the parameter in the method's signature;
- `values` - a list of values to trigger on;
- `protected` - if `true` the parameter will be treated as a protected
variable.

For example, if there is a need to disable a specific task based on some
variable in the process' context, it can be achieved with a policy:

```json
{
  "task": {
    "deny": [
      {
        "taskName": "ansible",
        "method": "execute",
        "params": [
          {
            "name": "gatekeeperResult",
            "index": 0,
            "values": [
              false,
              null
            ],
            "protected": true
          }
        ],        
        "msg": "I won't run Ansible without running the Gatekeeper task first"
      }
    ]
  }
}
```

In this example, because the Ansible's plugin method `execute` accepts
a `Context`, the policy executor looks for a `gatekeeperResult` in
the process' context.

## Workspace Rule

The workspace rule allows control of the overall size of the process'
workspace.

The syntax:

```json
{    
  "maxSizeInBytes": 1024,
  "ignoredFiles": ["...filename patterns..."],    
  "msg": "optional message"
}
```

The attributes:

- `maxSizeInBytes` - maximum allowed size of the workspace minus the
`ignoredFiles` (in bytes);
- `ignoredFiles` - list of filename patterns (regular expressions). The
matching files will be excluded from the total size calculation.

Example:

```json
{
  "workspace": {
    "msg": "Workspace too big (allowed size is 256Mb, excluding '.git')",
    "ignoredFiles": [
      ".*/\\.git/.*"
    ],
    "maxSizeInBytes": 268435456
  }
}
```

## Runtime Rule

The runtime rule controls allowed runtime(s) for process execution.

The syntax:

```json
{ 
  "msg": "optional message", 
  "runtimes": ["concord runtime(s)..."]
}
```

The attributes:

- `runtimes` - List of allowed concord runtime(s);

Example:

```json
{
  "runtime": {
    "msg": "{0} runtime version is not allowed",
    "runtimes": ["concord-v2"]
  }
}
```

## RawPayload Rule

RawPayload rules allows you to limit the size of the raw payload archive sent to start the process.

The syntax:

```json
{
   "rawPayload": {
      "msg": "Raw payload size too big: current {0} bytes, limit {1} bytes",
      "maxSizeInBytes": 1024
   }
}
```

## CronTrigger Rule

Cron trigger rule allows you, administrators to set the minimum interval between the process triggered by cron.

The syntax:

```json
{
  "cronTrigger": {
    "minInterval": "interval in seconds"
  }
}
```

For example:

```json
{
  "cronTrigger": {
    "minInterval": 61
  }
}
```
