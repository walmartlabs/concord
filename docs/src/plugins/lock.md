# Lock

The `lock` and `unlock` tasks provide methods to allow exclusive execution between
one or more running Concord processes.

- [Usage](#usage)
- [Parameters](#parameters)
- [Acquire and Release Lock](#acquire-and-release-lock)
- [Considerations](#considerations)

## Usage

To be able to use the task in a Concord flow, it must be added as a
[dependency](../processes-v2/configuration.html#dependencies):

```yaml
configuration:
  dependencies:
    - mvn://com.walmartlabs.concord.plugins.basic:lock-tasks:{{ site.concord_core_version }}
```

This adds the task to the classpath and allows you to invoke it in any flow.

## Parameters

- `name` - string, lock name used across processes to match exclusivity
- `scope` - string, scope to apply exclusivity. One of `PROJECT`, `ORG`

## Acquire and Release Lock

Acquire a lock in order to execute mutually exclusive steps across individual
processes across a Concord Project or Organization. For example, deployments to
a particular environment may need to be blocked while an existing deployment or
integration testing is active.

```yaml
# acquire lock so it's safe to deploy
- task: lock
  in:
    name: my-app-deployment
    scope: PROJECT

# perform the deployment...

# release lock
- task: unlock
  in:
    name: my-app-deployment
    scope: PROJECT
```

## Considerations

If the process does not immediately acquire a lock, then it suspends execution
until the lock is acquired. Temporary files in the working directory created at
runtime are cleaned up when processes suspend and resume. If set,
[`suspendTimeout` settings](../processes-v2/configuration.html#suspend-timeout) apply.

[Exclusive process configuration](../processes-v2/configuration.html#exclusive-execution)
is preferable to locking at runtime due to the more straightforward application
of exclusivity. The `lock` task enables mutual exclusivity across disparate
workflow repositories.
