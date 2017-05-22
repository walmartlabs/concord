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
      - log: "email sending error"
  - if: ${result.ok}                              # (2)
    then:
      - reportSuccess                             # (3)
    else:
      - log: "failure :-( ${lastError.message}"
    
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
- `error` block - to handle any exceptions thrown by the evaluation.
Exceptions are wrapped in `BpmnError` type.

See [the list of automatically provided variables](./processes.md#provided-variables).

Literal values (e.g. arguments or [form](#forms) field values) can
contain expressions:
```yaml
main:
  - log: ["red", "green", "${colors.blue}"]
  - myTask: { nested: { literals: "${myOtherTask.doSomething()}"} }
```

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
  
  # multiline strings and string interpolation is also supported
  - log: |
      those line breaks will be
      preserved. Here will be a ${result} of EL evaluation.
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

### Return error command

The `return error` command can be used to stop the execution of the current
(sub) flow and throw error:

```yaml
main:
  - if: ${myVar > 0}
    then:
      - log: moving along
    else:
      - return: error-code
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

### Scripting

Most of the JSR-223 compatible script engines are supported:

```yaml
main:
  - script: js
    body: |
      function doSomething(i) {
        return i * 2;
      }

      var x = execution.getVariable("input");
      execution.setVariable("output", doSomething(x));
```

External scripts can also be used:
```yaml
main:
  - script: my_scripts/test.js
```

Path to a script must be relative to the root directory of a workspace.

See [the expressions](#expressions) section for the list of provided
global variables.

JavaScript content is executed using Java's Nashorn engine. All other
engines require additional dependencies to be included with the process
definition.

## Form Syntax

### Declaring a new form

Forms are declared at the top level of a YAML document, just like the
processes. Form label format: `form ($FORM_NAME)`.

```yaml
form (myForm):
  - ...
```

The name of a form (in this example it's `myForm`) can be used to
[call a form](#calling-a-form) from a process. Also, it will be used
as a name of a map object which stores the values of the fields.

### Form fields

Forms must contain one or more fields:

```yaml
form (myForm):
  - fullName: { label: "Name", type: "string", pattern: ".* .*" }
  - age: { label: "Age", type: "int", min: 21, max: 100 }
  - favouriteColour: { label: "Favourite colour", type: "string", allow: ["gray", "grey"] }
  - languages: { label: "Preferred languages", type: "string+", allow: "${locale.languages()}" }
```

Field declaration consists of the name (`myField`), the type
(`string`) and additional options.

The name of a field will be used to store a field's value in the
form's results. E.g. if the form's name is `myForm` and the field's
name is `myField`, then the value of the field will be stored in
`myForm.myField` variable.

Common options:
- `label`: the field's label, usually human-readable;
- `value`: default value [expression](#expressions), evaluated when
the form is called;
- `allow`: allowed value(s). Can be a YAML literal, array, object or an
[expression](#expressions).

Supported types of fields and their options:
- `string`: a string value
  - `pattern`: (optional) a regular expression to check the value.
- `int`: an integer value
  - `min`, `max`: (optional) value bounds.
- `decimal`: a decimal value
  - `min`, `max`: (optional) value bounds.
  
Cardinality of the field can be specified by adding a cardinality
quantifier to the type:
- a single non-optional value: `string`;
- optional value: `string?`;
- one or more values: `string+`;
- zero or more values: `string*`.

Additional field types will be added in the next versions.

### Calling a form

To call a form from a process, use `form` command:

```yaml
main:
  - form: myForm
  - ${log.info("test", myForm.myField)}
```

Forms will be pre-populated with values if the current context contains
a map object, stored under the form's name. E.g. if the context has
a map object

```json
{
  "myForm": {
    "myField": "my string value"
  }
}
```

then the form's `myField` will be populated with `my string value`.

The `form` command accepts additional options:
```yaml
main:
  - form: myForm
    param1: value
    param2: 123
```

Supported options:
- `yield`: a boolean value. If `true`, the UI wizard will stop after this
form and the rest of the process will continue in "background". Supported
only for custom (with branding) forms.

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
formCallOptions := (kv)*

exprShort := expression
exprFull := FIELD_NAME "expr" expression exprOptions
taskFull := FIELD_NAME "task" VALUE_STRING taskOptions
taskShort := FIELD_NAME literal
ifExpr := FIELD_NAME "if" expression FIELD_NAME "then" steps (FIELD_NAME "else" steps)?
returnExpr := VALUE_STRING "return"
returnErrorExpr := FIELD_NAME "return" VALUE_STRING
group := FIELD_NAME ":" steps groupOptions
callProc := VALUE_STRING
script := FIELD_NAME "script" VALUE_STRING (FIELD_NAME "body" VALUE_STRING)?
formCall := FIELD_NAME "form" VALUE_STRING formCallOptions

stepObject := START_OBJECT group | ifExpr | exprFull | formCall | taskFull | inlineScript | taskShort END_OBJECT
step := returnExpr | returnErrorExpr | exprShort | callProc | stepObject
steps := START_ARRAY step+ END_ARRAY

formField := START_OBJECT FIELD_NAME object END_OBJECT
formFields := START_ARRAY formField+ END_ARRAY

procDef := FIELD_NAME steps
formDef := formName formFields

defs := START_OBJECT (formDef | procDef)+ END_OBJECT
```

