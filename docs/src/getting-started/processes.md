# Processes

A process is an execution of flows written in [Concord DSL](../processes-v1/index.md#dsl)
running in a [project](../getting-started/projects.md) or standalone.
A process can represent a single deployment, CI/CD job or any other, typically
a "one-off", type of workload.

Let's take a look at an example:

```yaml
configuration:
  arguments:
    todoId: "1"

flows:
  default:
    - task: http
      in:
        url: "https://jsonplaceholder.typicode.com/todos/${todoId}"
        response: json
        out: myTodo

    - if: "${myTodo.content.completed}"
      then:
        - log: "All done!"
      else:
        - log: "You got a todo item: ${myTodo.content.title}"
```

When executed this flow performs a number of steps:
- fetches a JSON object from the specified URL;
- saves the response as a flow variable;
- checks if the retrieved "todo" is completed or not;
- prints out a message depending whether the condition is true or not. 

The example demonstrates a few concepts:
- flow definitions use Concord's YAML-based [DSL](../processes-v1/index.md#dsl);
- flows can call [tasks](../getting-started/tasks.md). And tasks can perform
useful actions;
- flows can use [conditional expressions](../processes-v1/flows.md#conditional-expressions);
- tasks can save their results as flow [variables](../processes-v1/flows.md#setting-variables)
- an [expression language](../processes-v1/flows.md#expressions) can be used to work
with data inside flows;

There are multiple ways how to execute a Concord process: using a Git
repository, sending the necessary files in [the API request](../api/process.md#start-a-process),
using a [trigger](../triggers/index.md), etc.

No matter how the process was started it goes through the same execution steps:

- project repository data is cloned or updated;
- binary payload from the process invocation is added to the workspace;
- configuration parameters from different sources are merged together;
- [imports](../processes-v2/imports.md) and [templates](../templates/index.md)
are downloaded and applied;
- the process is added to the queue;
- one of the agents picks up the process from the queue;
- the agent downloads the process state,
[dependencies](../processes-v2/configuration.md#dependencies) and `imports`;
- the agent starts [the runtime](#runtime) in the process' working directory;
- the flow configured as entry point is invoked.

During its life, a process can go though various statuses:

- `NEW` - the process start request is received, passed the initial validation
and saved for execution;
- `PREPARING` - the start request is being processed. During this status,
the Server prepares the initial process state;
- `ENQUEUED` - the process is ready to be picked up by one of the Agents;
- `WAITING` - the processes is waiting for "external" conditions 
(e.g. concurrent execution limits, waiting for another process or lock, etc);
- `STARTING` - the process was dispatched to an Agent and is being prepared to
start on the Agent's side;
- `RUNNING` - the process is running;
- `SUSPENDED` - the process is waiting for an external event (e.g. a form);
- `RESUMING` - the Server received the event the process was waiting for and
now prepares the process' resume state;
- `FINISHED` - final status, the process was completed successfully. Or, at
least, all process-level errors were handled in the process itself;
- `FAILED` - the process failed with an unhandled error;
- `CANCELLED` - the process was cancelled by a user;
- `TIMED_OUT` - the process exceeded its
[execution time limit](#process-timeout).

## Runtime

The runtime is what actually executes the process. It is an interpreter written
in Java that executes flows written in [Concord DSL](../processes-v1/index.md#dsl).
Typically this is executed in a separate JVM process.

Currently there are two versions of the runtime:
- [concord-v1](../processes-v1/index.md) - used by default;
- [concord-v2](../processes-v2/index.md) - new and improved version
introduced in 1.42.0.

The runtime can be specified using `configuration.runtime` parameter in
the `concord.yml` file:

```yaml
configuration:
  runtime: "concord-v2"
```

or in the request parameters:

```
$ curl -F runtime=concord-v2 ... https://concord.example.com/api/v1/process
```

## Process Events

During process execution, Concord records various events: process status
changes, task calls, internal plugin events, etc. The data is stored in the
database and used later in the [Concord Console](../console/index.md) and
other components.

Events can be retrieved using [the API](../api/process.md#list-events).
Currently, those event types are:

- `PROCESS_STATUS` - process status changes;
- `ELEMENT` - flow element events (such as task calls).

In addition, plugins can use their own specific event types. For example, the
[Ansible plugin]({{ site.concord_plugins_v2_docs }}/ansible.md) uses custom events to record playbook
execution details.  This data is extensively used by the Concord Console to
provide visibility into the playbook execution - hosts, playbook steps, etc.

Event recording can be configured in the [Runner](../processes-v1/configuration.md#runner)
section of the process' `configuration` object.
