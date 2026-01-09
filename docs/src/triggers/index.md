# Overview

Triggers provide a way to automatically start specific Concord flows as a
response to specific events.

- [Common Syntax](#common-syntax)
- [Supported Triggers](#supported-triggers)
- [Exclusive Triggers](#exclusive-triggers)
- [Security](#security)  
- [Limitations](#limitations)

## Common Syntax

All triggers work by the same process:

- Concord matches the patterns you specify as triggers to event data.
- event data is typically external, but can be internally produced in the case
of the [cron triggers](./cron.md).
- for each matched trigger, it starts a new process.

You define triggers in the `triggers` section of a `concord.yml` file, as in
this example:

```yaml
triggers:
- eventSource:
    parameter1: ".*123.*"
    parameter2: false
    entryPoint: myFlow
    activeProfiles:
    - myProfile
    arguments:
      myValue: "..."
    exclusive:
      group: "myGroup"
      mode: cancel
...
```

When the API end-point `/api/v1/events/` receives an event, Concord detects any
existing matches with trigger names.

This allows you to publish events to `/api/v1/events/eventSource` for matching
with triggers (where `eventSource` is any string).

Further:

- Concord detects any matches of `parameter1` and `parameter2` with the external
  event's parameters;
- `entryPoint` is the name of the flow that Concord starts when there is a match;
- `activeProfiles` is the list of [profiles](../processes-v1/profiles.md)
  to active for the process;
- `arguments` is the list of additional parameters that are passed to the flow;
- `exclusive` is the exclusivity info of the [exclusive group](#exclusive-triggers).

Parameters can contain YAML literals as follows:

- strings
- numbers
- boolean values
- regular expressions

The `triggers` section can contain multiple trigger definitions. Each matching
trigger is processed individually--each match can start a new process.

A trigger definition without match attributes is activated for any event
received from the specified source.

In addition to the `arguments` list, a started flow receives the `event`
parameter which contains attributes of the external event. Depending on the
source of the event, the exact structure of the `event` object may vary.

## Supported Triggers

- [GitHub](./github.md)
- [Cron](./cron.md)
- [Manual](./manual.md)
- [Generic](./generic.md)
- [OneOps](./oneops.md)

## Exclusive Triggers

There is an option to make a triggered processes "exclusive". This prevents
the process from running, if there are any other processes in the same project
with the same "exclusive group":

```yaml
flows:
  cronEvent:
    - log: "Hello!"
    - ${sleep.ms(65000)} # wait for 1m 5s

triggers:
- cron:
    spec: "* * * * *" # run every minute
    timezone: "America/Toronto"
    entryPoint: cronEvent
```

In this example, if the triggered process runs longer than the trigger's period,
then it is possible that multiple `cronEvent` processes can run at the same
time. In some cases, it is necessary to enforce that only one trigger process
runs at a time, due to limitation in target systems being accessed or similar
reasons.
  
```yaml
triggers:
- cron:
    spec: "* * * * *"
    timezone: "America/Toronto"
    entryPoint: cronEvent
    exclusive:
      group: "myGroup"
      mode: "cancel" # or "wait"
```

Any processes with the same `exclusive` value are automatically prevented from
starting, if a running process in the same group exists. If you wish to enqueue
the processes instead use `mode: "wait"`.

See also [Exclusive Execution](../processes-v1/configuration.md#exclusive-execution)
section in the Concord DSL documentation.

## Security

Triggering a project process requires at least
[READER-level privileges](../getting-started/orgs.md#teams).

To activate a trigger using the API, the request must be correctly
authenticated first. To activate a [generic trigger](./generic.md) one can
use an API request similar to this:

```
curl -ik \
 -H 'Authorization: <token>' \
 -H 'Content-Type: application/json' \
 -d '{"some_value": 123}'
 https://concord.example.com/api/v1/events/my_trigger
```

The owner of the `token` must have the necessary privileges in all projects
that have such triggers.

Processes started by triggers are executed using the request sender's
privileges. If the process uses any Concord resources such as
[secrets](../getting-started/security.md#secret-management) or
[JSON stores](../getting-started/json-store.md), the user's permissions need
to be configured accordingly.

## Limitations

Trigger configuration is typically loaded automatically, but can be disabled
globally or for specific types of repositories. For example, personal Git
repositories can be treated differently from organizational repositories in
GitHub. You can force a new parsing and configuration by manually reloading the
repository content with the **`Refresh`** button beside the repository in
the Concord Console or by
[using the API](../api/repository.md#refresh-repository).
