# Flows

Concord flows consist of series of steps executing various actions: calling
plugins (also known as "tasks"), performing data validation, creating
[forms](../getting-started/forms.md) and other steps.

The `flows` section should contain at least one flow definition:

```yaml
flows:
  default:
    ...

  anotherFlow:
    ...
```

Each flow must have a unique name and at least one [step](#steps).

## Steps

Each flow is a list of steps:

```yaml
flows:
  default:
    - log: "Hello!"

    - if: "${1 > 2}"
      then:
        - log: "How is this possible?"

    - log: "Bye!"
```

Flows can contain any number of steps and call each other. See below for
the description of available steps and syntax constructs.

- [Expressions](#expressions)
- [Conditional Expressions](#conditional-expressions)
- [Return Command](#return-command)
- [Exit Command](#exit-command)
- [Groups of Steps](#groups-of-steps)
- [Calling Other Flows](#calling-other-flows)
- [Loops](#loops)
- [Error Handling](#error-handling)
- [Retry](#retry)
- [Throwing Errors](#throwing-errors)
- [Setting Variables](#setting-variables)
- [Checkpoints](#checkpoints)

### Expressions

Expressions must be valid
[Java Expression Language EL 3.0](https://github.com/javaee/el-spec) syntax
and can be simple evaluations or perform actions by invoking more complex code.

Short form:
```yaml
flows:
  default:
    # calling a method
    - ${myBean.someMethod()}

    # calling a method with an argument
    - ${myBean.someMethod(myContextArg)}

    # literal values
    - ${1 + 2}

    # EL 3.0 extensions:
    - ${[1, 2, 3].stream().map(x -> x + 1).toList()}
```

Full form:
```yaml
flows:
  default:
    - expr: ${myBean.someMethod()}
      out: myVar
      error:
        - ${log.error("something bad happened")}
```

Full form can optionally contain additional declarations:
- `out` field: contains the name of a variable, in which a result of
the expression will be stored;
- `error` block: to handle any exceptions thrown by the evaluation.
Exceptions are wrapped in `BpmnError` type.

Literal values, for example arguments or [form](../getting-started/forms.md)
field values, can contain expressions:

```yaml
flows:
  default:
    - myTask: ["red", "green", "${colors.blue}"]
    - myTask: { nested: { literals: "${myOtherTask.doSomething()}"} }
```

Classes from the package `java.lang` can be accessed via EL syntax:

```
    - log: "Process running on ${System.getProperty('os.name')}"
```

### Conditional Expressions

```yaml
flows:
  default:
    - if: ${myVar > 0}
      then:                           # (1)
        - log: it's clearly non-zero
      else:                           # (2)
        - log: zero or less

    - ${myBean.acceptValue(myVar)}    # (3)
```

In this example, after `then` (1) or `else` (2) block are completed,
the execution continues with the next step in the flow (3).

"And", "or" and "not" operations are supported as well:
```yaml
flows:
  default:
    - if: ${true && true}
      then:
      - log: "Right-o"
    - if: ${true || false}
      then:
      - log: "Yep!"
    - if: ${!false}
      then:
      - log: "Correct!"
```

To compare a value (or the result of an expression) with multiple
values, use the `switch` block:

```yaml
flows:
  default:
    - switch: ${myVar}
      red:
        - log: "It's red!"
      green:
        - log: "It's definitely green"
      default:
        - log: "I don't know what it is"

    - log: "Moving along..."
```

In this example, branch labels `red` and `green` are the compared
values and `default` is the block which will be executed if no other
value fits.

Expressions can be used as branch values:

```yaml
flows:
  default:
    - switch: ${myVar}
      ${aKnownValue}:
        - log: "Yes, I recognize this"
      default:
        - log: "Nope"
```

### Return Command

The `return` command can be used to stop the execution of the current (sub) flow:

```yaml
flows:
  default:
    - if: ${myVar > 0}
      then:
        - log: moving along
      else:
        - return
```

The `return` command can be used to stop the current process if called from an
entry point.

### Exit Command

The `exit` command can be used to stop the execution of the current process:

```yaml
flows:
  default:
    - if: ${myVar > 0}
      then:
        - exit
    - log: "message"
```

The final status of a process after calling `exit` is `FINISHED`.

### Groups of Steps

Several steps can be grouped into one block. This allows `try-catch`-like
semantics:

```yaml
flows:
  default:
    - log: a step before the group

    - try:
      - log: "a step inside the group"
      - ${myBean.somethingDangerous()}
      error:
        - log: "well, that didn't work"
```

### Calling Other Flows

Flows, defined in the same YAML document, can be called by their names or using
the `call` step:

```yaml
flows:
  default:
  - log: hello

  # short form: call another flow by its name
  - mySubFlow

  # full form: use `call` step
  - call: anotherFlow
    # (optional) additional call parameters
    in:
      msg: "Hello!"

  - log: bye

  mySubFlow:
  - log: "a message from the sub flow"

  anotherFlow:
  - log: "message from another flow: ${msg}"
```

### Loops

Concord flows can iterate through a collection of items in a loop using the
`call` step and the `withItems` collection of values:

```yaml
  - call: myFlow
    withItems:
    - "first element"
    - "second element"
    - 3
    - false

  # withItems can also be used with tasks
  - task: myTask
    in:
      myVar: ${item}
    withItems:
    - "first element"
    - "second element"
```

The collection of items to iterate over can be provided by an expression:

```yaml
configuration:
  arguments:
    myItems:
    - 100500
    - false
    - "a string value"

flows:
  default:
  - call: myFlow
    withItems: ${myItems}
```

The items are referenced in the invoked flow with the `${item}` expression:

```yaml
  myFlow:
  - log: "We got ${item}"
```

Maps (dicts, in Python terms) can also be used:

```yaml
flows:
  default:
    - call: log
      in:
        msg: "${item.key} - ${item.value}"
      withItems:
        a: "Hello"
        b: "world"
```

In the example above `withItems` iterates over the keys of the object. Each
`${item}` provides `key` and `value` attributes.

Lists of nested objects can be used in loops as well:

```yaml
flows:
  default:
  - call: deployToClouds
    withItems:
    - name: cloud1
      fqdn: cloud1.myapp.example.com
    - name: cloud2
      fqdn: cloud2.myapp.example.com

  deployToClouds:
  - log: "Starting deployment to ${item.name}"
  - log: "Using fqdn ${item.fqdn}"
```

### Error Handling

The full form syntax allows using input variables (call arguments) and supports
error handling.

Task and expression errors are normal Java exceptions, which can be
\"caught\" and handled using a special syntax.

Expressions, tasks, groups of steps and flow calls can have an
optional `error` block, which will be executed if an exception occurs:

```yaml
flows:
  default:
  # handling errors in an expression
  - expr: ${myTask.somethingDangerous()}
    error:
    - log: "Gotcha! ${lastError}"

  # handling errors in tasks
  - task: myTask
    error:
    - log: "Fail!"

  # handling errors in groups of steps
  - try:
    - ${myTask.doSomethingSafe()}
    - ${myTask.doSomethingDangerous()}
    error:
    - log: "Here we go again"

  # handling errors in flow calls
  - call: myOtherFlow
    error:
    - log: "That failed too"
```

The `${lastError}` variable contains the last caught
`java.lang.Exception` object.

If an error was caught, the execution will continue from the next step:

```yaml
flows:
  default:
  - try:
      - throw: "Catch that!"
    error:
    - log: "A"

  - log: "B"
```

An execution logs `A` and then `B`.

When a process is cancelled (killed) by a user, a special flow
`onCancel` is executed:

```yaml
flows:
  default:
  - log: "Doing some work..."
  - ${sleep.ms(60000)}

  onCancel:
  - log: "Pack your bags. Show's cancelled"
```

**Note:** `onCancel` handler processes are dispatched immediately when the process
cancel request is sent. Variables set at runtime may not have been saved to the
process state in the database and therefore may be unavailable or stale in the
handler process.

Similarly, `onFailure` flow is executed if a process crashes (moves into the `FAILED` state):

```yaml
flows:
  default:
  - log: "Brace yourselves, we're going to crash!"
  - throw: "Crash!"

  onFailure:
  - log: "Yep, we just did"
```

In both cases, the server starts a _child_ process with a copy of
the original process state and uses `onCancel` or `onFailure` as an
entry point.

**Note:** `onCancel` and `onFailure` handlers receive the _last known_
state of the parent process' variables. This means that changes in
the process state are visible to the _child_ processes:

```yaml
flows:
  default:
  # let's change something in the process state...
  - set:
      myVar: "xyz"

  # will print "The default flow got xyz"
  - log: "The default flow got ${myVar}"

  # ...and then crash the process
  - throw: "Boom!"

  onFailure:
  # will log "I've got xyz"
  - log: "I've got ${myVar}"

configuration:
  arguments:
    # original value
    myVar: "abc"
```

In addition, `onFailure` flow receives `lastError` variable which
contains the parent process' last (unhandled) error:

```yaml
flows:
  default:
  - throw: "Kablam!"
        
  onFailure:
  - log: "${lastError.cause}"
``` 

Nested data is also supported:
```yaml
flows:
  default:
  - throw:
      myCause: "I wanted to"
      whoToBlame:
        mainCulpit: "${currentUser.username}"
        
  onFailure:
  - log: "The parent process failed because ${lastError.cause.payload.myCause}."
  - log: "And ${lastError.cause.payload.whoToBlame.mainCulpit} is responsible for it!"
```

If an `onCancel` or `onFailure` flow fails, it is automatically
retried up to three times.

### Retry

The `retry` attribute is used to restart the `task`/`flow` automatically
in case of errors or failures. Users can define the number of times the `task`/`flow` can
be re-tried and a delay for each retry.

- `delay` - the time span after which it retries. The delay time is always in 
seconds, default value is `5`;
- `in` - additional parameters for the retry
- `times` - the number of times a task/flow can be retried;
  
For example the below section executes the `myTask` using the provided `in`
parameters.  In case of errors, the task retries up to 3 times with 3
seconds delay each. Additional parameters for the retry are supplied in the
`in` block.

```yaml
- task: myTask
  in:
    ...
  retry:
    in:
      ...additional parameters...
    times: 3
    delay: 3
```
Retry flow call: 

```yaml
- call: myFlow
  in:
    ...
  retry:
    in:
      ...additional parameters...
    times: 3
    delay: 3
```

The default `in` and `retry` variables with the same values are overwritten.

In the example below the value of `someVar` is overwritten to 321 in the
`retry` block..


```yaml
- task: myTask
  in:
    someVar:
      nestedValue: 123
  retry:
    in:
      someVar:
        nestedValue: 321
        newValue: "hello"
```

The `retry` block also supports expressions:

```yaml
configuration:
  arguments:
    retryTimes: 3
    retryDelay: 2

flows:
  default:
    - task: myTask
      retry:
        times: "${retryTimes}"
        delay: "${retryDelay}"
```

### Throwing Errors

The `throw` step can be used to throw a new `RuntimeException` with the supplied message anywhere in a flow including in `error` sections and in
[conditional expressions](#conditional-expressions) such as if-then or
switch-case.

```yaml
flows:
  default:
  - try:
    - log: "Do something dangerous here"
    error:
    - throw: "oh, something went wrong."
```

Alternatively a caught exception can be thrown again using the `lastError` variable:

```yaml
flows:
  default:
  - try:
    - log: "Do something dangerous here"
    error:
    - throw: ${lastError}
```

### Setting Variables

The `set` step can be used to set variables in the current process context:

```yaml
flows:
  default:
  - set:
      a: "a-value"
      b: 3
  - log: ${a}
  - log: ${b}
```

Nested data can be updated using the `.` syntax:

```yaml
configuration:
  arguments:
    myComplexData:
      nestedValue: "Hello"

flows:
  default:
  - set:
      myComplexData.nestedValue: "Bye"

  # will print "Bye, Concord"
  - log: "${myComplexData.nestedValue}, Concord"
```

A [number of variables](./index.md#variables) are automatically set in each
process and available for usage.

**Note:** all variables are global. Consider the following example:

```yaml
flows:
  default:
    - set:
        x: "abc"

    - log: "${x}"         # prints out "abc"

    - call: aFlow

    - log: "${x}"         # prints out "xyz"

  aFlow:
    - log: "${x}"         # prints out "abc"

    - set:
        x: "xyz"
```

In the example above, even if the second `set` is called inside a subflow, its
value becomes available in the caller flow.

The same applies to nested data:
```yaml
flows:
  default:
    - set:
        nested:
          x: "abc"

    - call: aFlow

    - log: "${nested.y}"  # prints out "xyz"

  aFlow:
    - set:
        nested.y: "xyz"
```


### Checkpoints

A checkpoint is a point defined within a flow at which the process state is
persisted in Concord. This process state can subsequently be restored and
process execution can continue. A flow can contain multiple checkpoints.

The [REST API](../api/checkpoint.md) can be used for listing and restoring
checkpoints. Alternatively you can restore a checkpoint to continue processing
directly from the Concord Console.

The `checkpoint` step can be used to create a named checkpoint:

```yaml
flows:
  default:
  - log: "Starting the process..."
  - checkpoint: "first"
  - log: "Continuing the process..."
  - checkpoint: "second"
  - log: "Done!"
```

The example above creates two checkpoints: `first` and `second`.
These checkpoints can be used to restart the process from the point after the
checkpoint's step. For example, if the process is restored using `first`
checkpoint, all steps starting from `Continuing the process...`
message and further are executed.

Checkpoint names can contain expressions:
```yaml
configuration:
  arguments:
    checkpointSuffix: "checkpoint"

flows:
  default:
  - log: "Before the checkpoint"
  - checkpoint: "first_${checkpointSuffix}"
  - log: "After the checkpoint"
```

Checkpoint names must start with a (latin) letter or a digit, can contain
whitespace, underscores `_`, `@`, dots `.`, minus signs `-` and tildes `~`.
The length must be between 2 and 128 characters. Here's the regular expression
used for validation:

```
^[0-9a-zA-Z][0-9a-zA-Z_@.\\-~ ]{1,128}$
```

Only process initiators, administrators and users with `WRITER` access level to
the process' project can restore checkpoints with the API or the user console.

After restoring a checkpoint, its name can be accessed using
the `resumeEventName` variable.

**Note:** files created during the process' execution are not saved during the
checkpoint creation.
