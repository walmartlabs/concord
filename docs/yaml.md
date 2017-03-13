# YAML support

Concord supports YAML as one of the formats for defining processes.

## Example

```yaml
main:
  - task: sendEmail                               # (1)
    in:
      to: me@localhost.local
      subject: Hello, Concord!
    out:
      result: operationResult
    error:
      - log: email sending error
  - if: ${result.ok}                              # (2)
    then:
      - reportSuccess                             # (3)
    else:
      - log: failure :-(
    
reportSuccess:
  - ${dbBean.updateStatus(result.id, "SUCCESS")}; # (4)
```

In this (kind of abstract) example:

- `sendEmail` task (1) is executed using two input parameters: `to` and
`subject`. The output of the task is stored in `result` variable.
- `if` expression (2) is used to either call `reportSuccess` sub-flow
(3) or to log a failure message;
- `reportSuccess` flow is calling a Java bean using the EL syntax (4).

## Process Syntax

### Entry points

Entry point is a top-level element of a document.
Concord uses entry points as a starting step of an execution.
A single YAML document can contain multiple entry points.

```yaml
main:
  - ...
  - ...

anotherEntry:
  - ...
  - ...
```

An entry point must be followed by one or more execution steps.

### Execution steps

#### Expressions

Expressions are used to invoke some 3rd-party code. All expressions
must be valid
[JUEL](https://en.wikipedia.org/wiki/Unified_Expression_Language)
(JSR 245) expressions. Currently,
[de.odysseus.juel/juel-impl](https://github.com/beckchr/juel/) is used
as an implementation.

Short form:
```yaml
main:
  # calling a method
  - ${myBean.someMethod()}
  
  # calling a method with an argument
  - ${myBean.someMethod(myContextArg)}
  
  # literal values
  - ${1 + 2}
```

Short form is simply a JUEL expression.

Full form:
```yaml
main:
  - expr: ${myBean.someMethod()}
    out: myVar
    error:
      - ${log.error("something bad happened")}
```

Full form optionally contains additional declarations:
- `out` field - contains the name of a variable, in which a result of
the expression will be stored;
- `error` block - to handle any `BPMNError` exceptions thrown by the
evaluation.

Concord provides a couple built-in variables:
- `execution` - a reference to a context variables map of a current
execution;
- `txId` - unique identifier of a current execution.

Availability of other variables and "beans" depends on installed
Concord's plugins and arguments passed on a process' start.
See also the document on
[how to create custom tasks](./extensions.md#tasks).

#### Tasks

There are other ways to call Java code: by using dynamic method
resolution or by using `JavaDelegate` instances.

Dynamic method resolution is the simplest way to call Java code.
Any object that implements `com.walmartlabs.concord.common.Task`
interface and provides a `call(...)` method сan be called this way.

```yaml
main:

  # calling a method with a single argument
  # same as $(log.call("hello")}
  - log: hello
  
  # calling a method with a single argument
  # the value will be a result of expression evaluation
  - log: ${myMessage}
  
  # calling a method with two arguments
  # same as $(log.call("warn", "hello")}
  - log: ["warn", "hello"]
  
  # calling a method with a single argument
  # the value will be converted into Map<String, Object>
  - log: { level: "warn", message: "hello" }
```

Alternatively, `JavaDelegate` instances can be used. While the support
for `JavaDelegate` tasks is mostly for compatibility reasons, it
provides additional features like in/out variables mapping.

```yaml
main:
  # calling a task with in/out variables mapping
  - task: myTask
    in:
      taskVar: ${processVar}
      anotherTaskVar: "a literal value"
    out:
      processVar: ${taskVar}
    error:
      - log: something bad happened
```

### Conditional expressions

```yaml
main:
  - if: ${myVar > 0}
    then:                           # (1)
      - log: it's clearly non-zero
    else:                           # (2)
      - log: zero or less

  - ${myBean.acceptValue(myVar)}    # (3)
```

In this example, after `then` (1) or `else` (2) block are completed,
the execution will continue from the next step in the flow (3).

### Return command

The `return` command can be used to stop the execution of the current
(sub) flow:

```yaml
main:
  - if: ${myVar > 0}
    then:
      - log: moving along
    else:
      - return
```

### Groups of steps

Several steps can be grouped in one block. This allows `try-catch`-like
semantic:

```yaml
main:
  - log: a step before the group
  - ::
    - log: a step inside the group
    - ${myBean.somethingDangerous()}
    error:
      - log: well, that didn't work
```

### Calling other flows

Flows, defined in the same YAML document, can be called by their names:

```yaml
main:
  - log: hello
  - mySubFlow
  - log: bye
  
mySubFlow:
  - log: a message from the sub flow
```

## Form Syntax

TBD.

## Grammar

Formal definition (PEG-like).

```
expression := VALUE_STRING ${.*}
value := VALUE_STRING | VALUE_NUMBER_INT | VALUE_NUMBER_FLOAT | VALUE_TRUE | VALUE_FALSE | VALUE_NULL | arrayOfValues | object
arrayOfValues := START_ARRAY value* END_ARRAY
object := START_OBJECT (FIELD_NAME value)* END_OBJECT
identifier := VALUE_STRING
formName := VALUE_STRING ^form \((.*)\)$

outField := FIELD_NAME "out" identifier
errorBlock := FIELD_NAME "error" steps

kv := FIELD_NAME value
inVars := FIELD_NAME "in" START_OBJECT (kv)+ END_OBJECT
outVars := FIELD_NAME "out" START_OBJECT (kv)+ END_OBJECT

exprOptions := (outField | errorBlock)*
taskOptions := (inVars | outVars | outField | errorBlock)*
groupOptions := (errorBlock)*
formCallOptions := (inVars | errorBlock)*

exprShort := expression
exprFull := FIELD_NAME "expr" expression exprOptions
taskFull := FIELD_NAME "task" VALUE_STRING taskOptions
taskShort := FIELD_NAME literal
ifExpr := FIELD_NAME "if" expression FIELD_NAME "then" steps (FIELD_NAME "else" steps)?
returnExpr := VALUE_STRING "return"
group := FIELD_NAME ":" steps groupOptions
callProc := VALUE_STRING
formCall := FIELD_NAME "form" VALUE_STRING formCallOptions

stepObject := START_OBJECT group | ifExpr | exprFull | formCall | taskFull | taskShort END_OBJECT
step := returnExpr | exprShort | callProc | stepObject
steps := START_ARRAY step+ END_ARRAY

formField := START_OBJECT FIELD_NAME object END_OBJECT
formFields := START_ARRAY formField+ END_ARRAY

procDef := FIELD_NAME steps
formDef := formName formFields

defs := START_OBJECT (formDef | procDef)+ END_OBJECT
```

