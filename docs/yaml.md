# YAML support

Concord supports YAML as one of the process description formats.

## Example

```yaml
main:
- expr: ${myBean.doSomething(myVar)}
- subprocess:
  steps:
  - expr: ${myBean.doSomethingElse(execution)}
  - switch:
    - expr: ${myVar == "abc"}
      steps:
      - end: customError
    - expr: ${myVar == "xyz"}
      steps:
      - expr: ${myBean.finish()}
  errors:
  - call: handleErrors

handleErrors:
- expr: ${myBean.reportAnError(execution)}
- end: anotherError
```

## Syntax

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

## Execution steps

### Expressions

Expressions are used to invoke some 3rd-party code. All expressions must be valid
[JUEL](https://en.wikipedia.org/wiki/Unified_Expression_Language) (JSR 245) expressions. Currently,
[de.odysseus.juel/juel-impl](https://github.com/beckchr/juel/) is used as an implementation.

```yaml
main:
- expr: "a literal"
- expr: ${true}
- expr: ${aContextVar}
- expr: ${myBean.methodInvocation()}
- expr: ${myBean.methodWithArgs(aContextVar, execution.getVariable("aContextVar")}
```

Concord provides a few "built-in" variables:
- `execution` - a reference to a context variables map of a current execution;
- `txId` - unique identifier of a current execution.

Availability of other variables and "beans" depends on installed Concord's plugins and arguments passed on a
process' start.

### Subprocesses

Subprocess is a group of steps. In the current version it mostly used to handle errors of a specific
subflow of a process. It is similar to Java's "try/catch" block.

```yaml
main:
- subprocess:
  steps:
  - ...
  - ...
  errors:
  - ref: myError
    call: someOtherProcess
      in:
        myVar: ${someVar}
```

If `ref` is specified, then only the errors with the same `ref` value will be handled in that section.
If `ref` is not specified, then all errors will be caught.
Multiple error handlers (or "catch" sections) can be defined.

### Calling other processes

A `call` step invokes the specified process by its entry point's name.

```yaml
main:
- call: somethingElse
  in:
    a: 123
    b: ${myBean.getSomeValue()}
    c: ${otherVar}
    anArray:
    - 1
    - 2
```

The `in` section is used to specify the input parameters (arguments) of a called process.

Value of a declared variable is a JUEL expression (please see the [Expressions](#expressions)
section in this document) or a literal one of supported types: string, number, boolean or a collection.

### Conditionals and branching

A `switch` step evaluates its `expr` elements in the order of declaration.
If one of the evaluations returns `true`, a corresponding `steps` section is executed.
If none of `expr` elements returns `true`, `switch` section is ignored and an execution continues
from the next step.

```yaml
main:
- switch:
  - expr: ${true}
    steps:
    - ...
  - expr: ${myBean.checkSomething()}
    steps:
    - ...
- ...
```

### End steps

An `end` step declares an exit point of a process.

```yaml
main:
- ...
- end:

anotherOne:
- ...
- end: someError
```

An optional error reference can be specified as a value of an `end` element.