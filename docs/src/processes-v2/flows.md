# Flows

Concord flows consist of series of steps executing various actions: calling
plugins (also known as "tasks"), performing data validation, creating
[forms](../getting-started/forms.md) and other steps.

- [Structure](#structure)
- [Steps](#steps)
    - [Task Calls](#task-calls)
      - [Task Result Data Structure](#task-result-data-structure) 
    - [Expressions](#expressions)
    - [Conditional Execution](#conditional-execution)
    - [Return Command](#return-command)
    - [Exit Command](#exit-command)
    - [Groups of Steps](#groups-of-steps)
    - [Calling Other Flows](#calling-other-flows)
    - [Setting Variables](#setting-variables)
    - [Checkpoints](#checkpoints)
    - [Parallel Execution](#parallel-execution)
- [Loops](#loops)
- [Error Handling](#error-handling)
    - [Handling Errors In Flows](#handling-errors-in-flows)
    - [Handling Cancellations, Failures and Timeouts](#handling-cancellations-failures-and-timeouts)
    - [Retry](#retry)
    - [Throwing Errors](#throwing-errors)
- [Testing in Concord](#testing-in-concord)

## Structure

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

    - if: ${1 > 2}
      then:
        - log: "How is this possible?"

    - log: "Bye!"
```

Flows can contain any number of steps and call each other. See below for
the description of available steps and syntax constructs.

### Task Calls

The `task` syntax can be used to call Concord [tasks](./tasks.md):

```yaml
flows:
  default:
    - task: log
      in:
        msg: "Hello!"
```

Input parameters must be explicitly passed in the `in` block. If the task
produces a result, it can be saved as a variable by using the `out` syntax:

```yaml
flows:
  default:
    - task: http
      in:
        url: "https://google.com"
      out: result

    - if: ${not result.ok}
      then:
        - log: "task failed: ${result.error}"
```

#### Task Result Data Structure

All returned result data from tasks compatible with runtime-v2 contain a common
set of fields, in addition to any task-specific data:

- `ok` - boolean, true when task executes without error;
- `error` - string, an error message when `ok` is `false`;

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
        - log: "whoops, something happened"
```

Full form can optionally contain additional declarations:
- `out` field - contains the name of a variable to store the result
of the expression;
- `error` block - to handle any exceptions thrown by the evaluation.

Literal values, for example arguments or [form](../getting-started/forms.md)
field values, can contain expressions:

```yaml
configuration:
  arguments:
    colors:
      blue: "blue"
    aFieldsInitialValue: "hello!"

flows:
  default:
    - task: myTask
      in:
        colors: ["red", "green", "${colors.blue}"]

    - task: myTask
      in:
        nested:
          literals: "${myOtherTask.doSomething()}"

forms:
  myForm:
    - aField: { type: "string", value: "${aFieldsInitialValue}" }
```

Classes from the package `java.lang` can be accessed via EL syntax:

```yaml
flows:
  default:
    - log: "Process running on ${System.getProperty('os.name')}"
```

#### Builtin functions

- `allVariables` - returns a Java Map object with all current variables;

```yaml
flows:
  default:
    # prints out: {projectInfo={orgId=0fac1b18-d179-11e7-b3e7-d7df4543ed4f, orgName=Default} ...}
    - log: ${allVariables()}
```

- `hasVariable` - accepts a variable name or nested variable path (as a string parameter) 
  and returns true if the variable exists;

```yaml
flows:
  default:
    # prints out: false
    - log: ${hasVariable('myVar')}
  
    - set:
        myVar2: "value"

    # prints out: true
    - log: ${hasVariable('myVar2')}
  
    - set:
        nullVar: ${null}

    # prints out: true
    - log: ${hasVariable('nullVar')}
  
    - set:
        a:
          b: 1

    # prints out: true
    - log: ${hasVariable('a.b')}
```

- `currentFlowName` - returns current flow name as string;

```yaml
flows:
  default:
    # prints out: default
    - log: ${currentFlowName()}

    - call: myFlow  

  myFlow:
    # prints out: myFlow
    - log: ${currentFlowName()}
```

- `evalAsMap` - evaluates the specified value as an expression, 
  returns a Java Map object;

```yaml
flows:
  default:
    - script: js
      body: |
        var x = {'a.b': 1, 'a.c': 2, 'a.d': '${a.b}', 'y': 'boo'};
        context.variables().set('x', x);

    # prints out: {a={b=1, d=1, c=2}, y=boo}
    - log: ${evalAsMap(x)}
```

- `hasNonNullVariable` - returns `true` if the process has the specified variable and its value
  is not `null`;

- `hasFlow` - returns `true` if the process has a specified flow

```yaml
flows:
  default:
    # prints out: 'true'
    - log: "'${hasFlow('myFlow')}'"

    # prints out: 'false'
    - log: "'${hasFlow('someUndefinedFlow')}'"
  myFlow:
    - log: "In my flow"
```

- `isDebug` - returns `true` if process started with debug flag;
- `isDryRun` - returns `true` if process stated in dry-run mode;

- `orDefault` - accepts a variable name (as a string parameter), default value and 
  returns variable value or default value;

```yaml
flows:
  default:
    # prints out: '1'
    - log: "'${orDefault('myVar', 1)}'"

    - set:
        myVar2: "boo"

    # prints out: 'boo'
    - log: "'${orDefault('myVar2', 'xyz')}'"


    - set:
        nullVar: ${null}

    # prints out: ''
    - log: "'${orDefault('nullVar', 1)}'"
```

- `throw` - accepts error message (as a string parameter) and throws exception with this message;

```yaml
flows:
  default:
    - expr: ${throw('Stop IT')}

    - log: "Unreachable"
```
- `uuid` - returns a randomly generated UUID as a string;

### Conditional Execution

Concord supports both `if-then-else` and `switch` steps:

```yaml
configuration:
  arguments:
    myInt: 123

flows:
  default:
    - if: ${myInt > 0}
      then:                           # (1)
        - log: it's clearly non-zero
      else:                           # (2)
        - log: zero or less

    - log: "myInt: ${myVar}"          # (3)
```

In this example, after `then` (1) or `else` (2) block are completed,
the execution continues with the next step in the flow (3).

The `if` expressions must evaluate to a boolean value or to string values
"true" or "false" (case-insensitive).

For example, the following flow prints out "Yep!":

```yaml
configuration:
  arguments:
    myString: "tRuE"

flows:
  default:
    - if: ${myString}
      then:
        - log: "Yep!"
      else:
        - log: "Nope!"
```

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
configuration:
  arguments:
    myVar: "green"

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
values and `default` is the block which is executed if no other
value fits.

Expressions can be used as branch values:

```yaml
configuration:
  arguments:
    myVar: "red"
    aKnownValue: "red"

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
    - log: "a step before the group"

    - try:
        - log: "a step inside the group"
        - ${myBean.somethingDangerous()}
      error:
        - log: "well, that didn't work"
```

See the [Error Handling](#error-handling) section for more details.

### Calling Other Flows

Flows can be called using the `call` step:

```yaml
flows:
  default:
    - log: hello

    - call: anotherFlow
      # (optional) additional call parameters
      in:
        msg: "Hello!"

    - log: bye

  anotherFlow:
    - log: "message from another flow: ${msg}"
```

A `call` step can optionally contain additional declarations:
- `in` - input parameters (arguments) of the call;
- `loop` - see the [Loops](#loops) section;
- `retry` - see [Retry](#retry) section.

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
    
    # prints out "Bye, Concord"
    - log: "${myComplexData.nestedValue}, Concord"
```

A [number of variables](./index.md#variables) are automatically set in each
process and available for usage.

**Note:** comparing to [the runtime v1](../processes-v1/flows.md#setting-variables),
the scoping rules are different - all variables, except for
`configuration.arguments` and automatically provided ones, are local variables
and must be explicitly returned using `out` syntax. For flow `calls` inputs are
implicit - all variables available at the call site are available inside
the called flow:

```yaml
flows:
  default:
    - set:
        x: "abc"

    - log: "${x}"       # prints out "abc"

    - call: aFlow         # implicit "in"

    - log: "${x}"         # still prints out "abc"

    - call: aFlow
      out:
        - x               # explicit "out"
  aFlow:
    - log: "${x}"         # prints out "abc"

    - set:
        x: "xyz"
```

The same rules apply to nested data - top-level elements are local variables
and any changes to them will not be visible unless exposed using `out`:

```yaml
flows:
  default:
    - set:
        myComplexData:
          nested: "abc"

    - log: "${myComplexData.nested}"  # prints out "abc"

    - call: aFlow

    - log: "${myComplexData.nested}"  # still prints out "abc"

    - call: aFlow
      out:
        - myComplexData

    - log: "${myComplexData.nested}"  # prints out "xyz"
  aFlow:
    - set:
        myComplexData.nested: "xyz"
```

### Checkpoints

A checkpoint is a point defined within a flow at which Concord persists
the process state. This process state can subsequently be restored and
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

### Parallel Execution

The `parallel` block executes all step in parallel:

```yaml
flows:
  default:
    - parallel:
        - ${sleep.ms(3000)}
        - ${sleep.ms(3000)}

    - log: "Done!"
```

The runtime executes each step in its own Java thread.

Variables that exist at the start of the `parallel` block are copied into each
thread.

The `out` block can be used to return variables from the `parallel`
block back into the flow:

```yaml
- parallel:
    - task: http
      in:
        url: https://google.com/
      out: googleResponse

    - task: http
      in:
        url: https://bing.com/
      out: bingResponse
  out:
    - googleResponse
    - bingResponse

- log: |
    Google: ${googleResponse.statusCode}
    Bing: ${bingResponse.statusCode}
```

**Note:** currently, to pass current variables into a `parallel` block,
the runtime performs a "shallow copy". If you're passing collections or
non-primitive objects in or out of the `parallel` block, you can
still modify the original variable:

```yaml
- set:
    anObject:
      aList: [ ]

- parallel:
    - ${anObject.aList.add(1)}
    - ${anObject.aList.add(2)}

- log: ${anObject.aList}
```

While `parallel` executes _steps_ in parallel, `loop` with `parallel mode` can be used
to perform same steps for each item in a collection. See the [Loops](#loops)
section for more details.

## Loops

Concord flows can iterate through a collection of items in a loop using
the `loop` syntax:

```yaml
- call: myFlow
  loop:
    items:
      - "first element"   # string item
      - "second element"
      - 3                 # a number
      - false             # a boolean value

# loop can also be used with tasks
- task: myTask
  in:
    myVar: ${item}
  loop:
    items:
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
    loop: 
      items: ${myItems}
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
    - task: log
      in:
        msg: "${item.key} - ${item.value}"
      loop:
        items:
          a: "Hello"
          b: "world"
```

In the example above `loop` iterates over the keys of the object. Each
`${item}` provides `key` and `value` attributes.

Lists of nested objects can be used in loops as well:

```yaml
flows:
  default:
    - call: deployToClouds
      loop:
        items:
          - name: cloud1
            fqdn: cloud1.myapp.example.com
          - name: cloud2
            fqdn: cloud2.myapp.example.com

  deployToClouds:
    - log: "Starting deployment to ${item.name}"
    - log: "Using FQDN ${item.fqdn}"
```

The `loop` syntax can be used to process items in parallel.
Consider the following example:

```yaml
configuration:
  runtime: concord-v2
  dependencies:
    - "mvn://com.walmartlabs.concord.plugins.basic:http-tasks:1.73.0"

flows:
  default:
    - task: http
      in:
        # imagine a slow API call here
        url: "https://jsonplaceholder.typicode.com/todos/${item}"
        response: json
      out: results # loop turns "results" into a list of results for each item
      loop:
        items:
          - "1"
          - "2"
          - "3"
        mode: parallel
        parallelism: 2 # optional number of threads 
        
    # grab titles from all todos
    - log: ${results.stream().map(o -> o.content.title).toList()}
```

In the example above, each item is processed in parallel in a separate OS
thread.

The parallel `loop` syntax is supported for the same steps as `loop`:
tasks, flow calls, groups of steps, etc.

## Error Handling

### Handling Errors In Flows

Task and expression errors are regular Java exceptions, which can be
"caught" and handled using a special syntax.

[Expressions](#expressions), tasks, [groups of steps](#groups-of-steps) and
[flow calls](#calling-other-flows) can have an optional `error` block, which
is executed if an exception occurs:

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

The `${lastError}` variable contains the last caught `java.lang.Exception`
object.

If an error is caught, the execution continues from the next step:

```yaml
flows:
  default:
    - try:
        - throw: "Catch that!"
      error:
        - log: "Caught an error: ${lastError}"
    
    - log: "Continue the execution..."
```

An execution logs `Caught an error` message and then `Continue the execution`.

### Handling Cancellations, Failures and Timeouts

When a process is `CANCELLED` (killed) by a user, a special flow
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

Similarly, `onFailure` flow executes if a process crashes (moves into
the `FAILED` state):

```yaml
flows:
  default:
    - log: "Brace yourselves, we're going to crash!"
    - throw: "Crash!"

  onFailure:
    - log: "Yep, we just crashed."
```

In both cases, the server starts a _child_ process with a copy of
the original process state and uses `onCancel` or `onFailure` as an
entry point.

**Note:** `onCancel` and `onFailure` handlers receive the _last known_
state of the parent process' variables. This means that changes in
the process state are visible to the _child_ processes:

```yaml
configuration:
  arguments:
    # original value
    myVar: "abc"

flows:
  default:
  # let's change something in the process state...
  - set:
      myVar: "xyz"

  # prints out "The default flow got xyz"
  - log: "The default flow got ${myVar}"

  # ...and then crash the process
  - throw: "Boom!"

  onFailure:
    # logs "I've got xyz"
    - log: "I've got ${myVar}"
```

In addition, `onFailure` flow receives `lastError` variable which
contains the parent process' last (unhandled) error:

```yaml
flows:
  default:
    - throw: "Kablamo!"
        
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

If the process runs longer than the specified [timeout](./configuration.md#running-timeout),
Concord cancels it and executes the special `onTimeout` flow:

```yaml
configuration:
  processTimeout: "PT1M" # 1 minute timeout

flows:
  default:
    - ${sleep.ms(120000)} # sleep for 2 minutes

  onTimeout:
    - log: "I'm going to run when my parent process times out"
```

If the process suspended longer that the specified [timeout](./configuration.md#suspend-timeout)
Concord cancels it and executes the special `onTimeout` flow:

```yaml
configuration:
  suspendTimeout: "PT1M" # 1 minute timeout

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

  onTimeout:
    - log: "I'm going to run when my parent process times out"
```

If an `onCancel`, `onFailure` or `onTimeout` flow fails, it is automatically
retried up to three times.

### Retry

The `retry` attribute can be used to re-run a `task`, group of steps or
a `flow` automatically in case of errors. Users can define the number of times
the call can be re-tried and a delay for each retry.

- `delay` - the time span after which it retries. The delay time is always in 
seconds, default value is `5`;
- `in` - additional parameters for next attempt;
- `times` - the number of times a task/flow can be retried.
  
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

The `throw` step can be used to throw a new `RuntimeException` with
the supplied message anywhere in a flow including in `error` sections and in
[conditional expressions](#conditional-execution) such as `if-then` or
`switch-case`.

```yaml
flows:
  default:
    - try:
        - log: "Do something dangerous here"
      error:
        - throw: "Oh no, something went wrong."
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

### Testing in Concord

Testing in Concord offers a comprehensive and flexible approach by leveraging powerful tools like
[mocks]({{ site.concord_plugins_v2_docs }}/mocks.md),
the [verify task]({{ site.concord_plugins_v2_docs }}/mocks.md#how-to-verify-task-calls),
[asserts]({{ site.concord_plugins_v2_docs }}/asserts.md) and [dry-run mode](../processes-v2/index.md#dry-run-mode).
These tools enable you to test process logic, simulate external dependencies, and validate
interactions without impacting real systems or data.

With these features, you can:

- **Isolate components** for targeted testing using mocks.
- **Verify task interactions** to ensure correct behavior.
- **Run entire flows safely** with dry-run mode to prevent side effects.

This combination allows for thorough, controlled testing of complex processes, ensuring your flows
are reliable and production-ready

#### Example Concord Flow: Application Deployment and Testing

This example demonstrates a complete Concord flow that simulates an application deployment process
followed by application tests. It utilizes mocks, the verify task, asserts task and dry-run mode to
ensure the deployment and testing logic work correctly without real side effects.

```yaml
flows:
  ##
  # Simple blue-green deployment process.
  # in:
  #   environment: string, mandatory, environment to deploy app.
  ##
  blueGreenDeployment:
    # Assert that we do not miss an input parameter
    - expr: "${asserts.hasVariable('environment')}"

    # Step 1: Check out code from Git
    - name: "Checkout source from GH"
      task: git
      in:
        action: "clone"
        url: "git.example.com:example-org/git-project"
        workingDir: "git-project"

    # Step 2: Build the application
    - name: "Building application"
      task: buildApplication
      in:
        sourcePath: "git-project"

    # Step 3: Deploy the application
    - name: "Deploying application"
      task: deployApplication
      in:
        environment: "${environment}"

    # Step 4: Run tests on the deployed application
    - call: runTests
      in:
        environment: "${environment}"
      out: testResult

    # Step 5: Conditional promotion or rollback
    - if: "${testResult == 'All tests passed'}"
      then:
        - name: "Promoting '${environment}' environment to production."
          task: promoteEnvironment
          in:
            environment: "${environment}"
      else:
        - name: "Tests failed. Rolling back deployment."
          task: rollbackEnvironment
          in:
            environment: "${environment}"

  ##
  #  Simple test for blueGreenDeployment flow
  ##
  blueGreenDeploymentFlowTest:
    # Assert that we running this flow in a dry-run mode, to prevent unintended changes to external systems
    - expr: "${asserts.dryRunMode}"

    - set:
        mocks:
          # Mock git task
          - task: git
            in:
              action: "clone"

          # Mock the build step
          - task: "buildApplication"
            in:
              sourcePath: "git-project"

          # Mock the deployment step
          - task: "deployApplication"
            in:
              environment: "test-env"

          # Mock the promote step
          - task: "promoteEnvironment"

    # call real deployment flow
    - call: blueGreenDeployment
      in:
        environment: "test-env"

    # verify git task called one time
    - expr: "${verify.task('git', 1).execute({'action': 'clone'})}"
    # verify buildApplication task called one time
    - expr: "${verify.task('buildApplication', 1).execute(mock.any())}"
    # verify deployApplication task called one time
    - expr: "${verify.task('deployApplication', 1).execute({'environment': 'test-env'})}"
    # verify promoteEnvironment task called one time
    - expr: "${verify.task('promoteEnvironment', 1).execute({'environment': 'test-env'})}"

profiles:
  blueGreenDeploymentFlowTest:
    flows:
      # Mock runTests flow
      runTests:
        - expr: "${asserts.assertEquals('test-env', environment)}"

        # emulate result from original flow
        - set:
            testResult: 'All tests passed'

configuration:
  runtime: concord-v2
  dependencies:
    - "mvn://com.walmartlabs.concord.plugins.basic:mock-tasks:{{site.concord_core_version}}"
    - "mvn://com.walmartlabs.concord.plugins.basic:asserts-tasks:{{site.concord_core_version}}"
```

This flow can be safely tested in dry-run mode to validate logic without making real deployments:

```bash
curl ... -FdryRun=true -FactiveProfiles=blueGreenDeploymentFlowTest -FentryPoint=blueGreenDeploymentFlowTest https://concord.example.com/api/v1/process
```
