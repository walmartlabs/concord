# Scripting

Concord flows can include scripting language snippets for execution. The
scripts run within the same JVM that is running Concord, and hence need to
implement the Java Scripting API as defined by JSR-223. Language examples with a
compliant runtimes are [JavaScript](#javascript), [Groovy](#groovy),
[Python](#python), [JRuby](#ruby) and many others.

Script engines must support Java 8.

Script languages have to be identified by setting the language explicitly or can be
automatically identified based on the file extension used. They can be stored
as external files and invoked from the Concord YAML file or they can be inline
in the file.

[Flow variables](#using-flow-variables), [Concord tasks](#using-concord-tasks) and other Java
methods can be accessed from the scripts due to the usage of the Java Scripting
API. The script and your Concord processes essentially run within the same
context on the JVM.

- [Using Flow Variables](#using-flow-variables)
  - [Flow Variables in Runtime V2](#flow-variables-in-runtime-v2)
- [Using Concord Tasks](#using-concord-tasks)
- [Error Handling](#error-handling)
- [Dry-run mode](#dry-run-mode)
- [Javascript](#javascript)
- [Groovy](#groovy)
- [Python](#python)
- [Ruby](#ruby)


## Using Flow Variables

For most of the supported languages, flow variables can be accessed
directly inside the script (without using ${} syntax):

```yaml
configuration:
  arguments:
    myVar: "world"

flows:
  default:
  - script: js
    body: |
      print("Hello, ", myVar)
```

If a flow variable contains an illegal character for a chosen scripting
language, it can be accessed using a built-in `execution` variable:

```yaml
- script: js
  body: |
    var x = execution.getVariable("an-illegal-name");
    print("We got", x);
```

To set a variable, you need to use the `execution.setVariable()` method:

```yaml
- script: js
  body: |
    execution.setVariable("myVar", "Hello!");
```

> Note that not every data structure of supported scripting languages is
> directly compatible with the Concord runtime. The values exposed to the flow
> via `execution.setVariable` must be serializable in order to work correctly
> with forms or when the process suspends. Refer to the specific language
> section for more details.

### Flow Variables in Runtime V2

Similar to Runtime V1, flow variables can be accessed directly inside the script
by the variable's name.

```yaml
configuration:
  runtime: concord-v2
  arguments:
    myVar: "world"

flows:
  default:
  - script: js
    body: |
      print("Hello, ", myVar);
```

Additionally, the `execution` variable has a `variables()` method which returns a
[`Variables` object](https://github.com/walmartlabs/concord/blob/master/runtime/v2/sdk/src/main/java/com/walmartlabs/concord/runtime/v2/sdk/Variables.java). This object includes a number of methods for interacting with flow variables.

```yaml
configuration:
  runtime: concord-v2

flows:
  default:
  - script: js
    body: |
      var myVar = execution.variables().getString('myString', 'world');
      print("Hello, ", myVar);
```

To set a variable, use the `execution.variables().set()` method:

```yaml
configuration:
  runtime: concord-v2

flows:
  default:
  - script: js
    body: |
      execution.variables().set('myVar', 'Hello, world!');
```

## Using Concord Tasks

Scripts can retrieve and invoke all tasks available for flows by name:

```yaml
- script: js
  body: |
    var slack = tasks.get("slack");
    slack.call(execution, "C5NUWH9S5", "Hi there!");
```

The number and type of arguments depend on the particular task's method. In
this example, the script calls `call` method of the [SlackTask](https://github.com/walmartlabs/concord/blob/1e053db578b9550e0aac656e1916eaf8f8eba0b8/plugins/tasks/slack/src/main/java/com/walmartlabs/concord/plugins/slack/SlackTask.java#L54)
instance.

The `execution` variable is an alias for [context](../processes-v1/index.md#context)
and automatically provided by the runtime for all supported script engines.

## External Scripts

Scripts can be automatically retrieved from an external server:

```yaml
- script: "http://localhost:8000/myScript.groovy"
```

The file extension in the URL must match the script engine's
supported extensions -- e.g. `.groovy` for the Groovy language, `.js`
for JavaScript, etc.

## Error Handling

Script can have an optional error block. It is executed when an exception occurs
in the script execution:

```yaml
- script: groovy
  body: |
    throw new RuntimeException("kaboom!")
  error:
    - log: "Caught an error: ${lastError.cause}"
```

Using external script file:

```yaml
- script: "http://localhost:8000/myScript.groovy"
  error:
    - log: "Caught an error: ${lastError.cause}"
```

## Dry-run mode

[Dry-run mode](../processes-v2/index.md#dry-run-mode) is useful for testing and validating
the flow logic before running it in production.

By default, script steps do not support dry-run mode. To enable a script to run in this mode,
you need to modify the script to support dry-run mode or mark script step as dry-run ready
using `meta` field of the step if you are confident it is safe to run.

An example of a script step marked as dry-run ready:

```yaml
flows:
  myFlow:
    - script: js
      body: |
        log.info('I'm confident that this script can be executed in dry-run mode!');
      meta:
        dryRunReady: true   # dry-run ready marker for this step
```

> **Important**: Use the `meta.dryRunReady` only if you are certain that the script is safe
> to run in dry-run mode

If you need to change the logic in the script depending on whether it is running in dry-run mode
or not, you can use the `isDryRun` variable. `isDryRun` variable is available to indicate whether
the process is running in dry-run mode:

```yaml
flows:
  default:
    - script: js
      body: |
        if (isDryRun) {
          log.info('running in DRY-RUN mode');
        } else {
          log.info('running in REGULAR mode');
        }
      meta:
        dryRunReady: true   # dry-run ready marker for this step is also needed in this case
```

## JavaScript

JavaScript support is built-in and doesn't require any external
dependencies. It is based on the
[Nashorn](https://en.wikipedia.org/wiki/Nashorn_(JavaScript_engine))
engine and requires the identifier `js`.
[Nashorn](https://wiki.openjdk.java.net/display/Nashorn/Main) is based on
ECMAScript, adds
[numerous extensions](https://wiki.openjdk.java.net/display/Nashorn/Nashorn+extensions).
including e.g. a `print` command.

Using an inline script:

```yaml
flows:
  default:
  - script: js
    body: |
      function doSomething(i) {
        return i * 2;
      }

      execution.setVariable("result", doSomething(2));

  - log: ${result} # will output "4"
```

Using an external script file:

```yaml
flows:
  default:
  - script: test.js
  - log: ${result}
```

```javascript
// test.js
function doSomething(i) {
  return i * 2;
}

execution.setVariable("result", doSomething(2));
```

### Compatibility

JavaScript objects must be converted to regular Java `Map` instances to be
compatible with the Concord runtime:

```yaml
flows:
  default:
    - script: js
      body: |
        var x = {a: 1};
        var HashMap = Java.type('java.util.HashMap');
        execution.setVariable('x', new HashMap(x));
    - log: "${x.a}"
```

Alternatively, a `HashMap` instance can be used directly in the JavaScript
code.

Similarly, JavaScript arrays (lists) must be converted into compatible
Java `List` objects:

```javascript
var arr = [1, 2, 3];
var ArrayList = Java.type('java.util.ArrayList');
execution.setVariable('x', new ArrayList(arr));
```

## Groovy

Groovy is another compatible engine that is fully-supported in Concord. It
requires the addition of a dependency to
[groovy-all](https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/) and
the identifier `groovy`. For versions 2.4.* and lower jar packaging is used in
projects, so the correct dependency is
e.g. `mvn://org.codehaus.groovy:groovy-all:2.4.12`. Versions `2.5.0` and higher
use pom packaging, which has to be added to the dependency declaration before
the version. For example: `mvn://org.codehaus.groovy:groovy-all:pom:2.5.21`.

```yaml
configuration:
  dependencies:
  - "mvn://org.codehaus.groovy:groovy-all:pom:2.5.21"
flows:
  default:
  - script: groovy
    body: |
      def x = 2 * 3
      execution.setVariable("result", x)
  - log: ${result}
```

The following example uses some standard Java APIs to create a date value in the
desired format.

```yaml
- script: groovy
   body: |
     def dateFormat = new java.text.SimpleDateFormat('yyyy-MM-dd')
     execution.setVariable("businessDate", dateFormat.format(new Date()))
- log: "Today is ${businessDate}"
```

### Compatibility

Groovy's `LazyMap` are not serializable and must be converted to regular Java
Maps:

```yaml
configuration:
  dependencies:
    - "mvn://org.codehaus.groovy:groovy-all:pom:2.5.21"

flows:
  default:
    - script: groovy
      body: |
        def x = new groovy.json.JsonSlurper().parseText('{"a": 123}') // produces a LazyMap instance
        execution.setVariable('x', new java.util.HashMap(x))
    - log: "${x.a}"
```

## Python

Python scripts can be executed using the [Jython](http://www.jython.org/)
runtime. It requires the addition of a dependency to
[jython-standalone](https://repo1.maven.org/maven2/org/python/jython-standalone)
located in the Central Repository or on another server and the identifier
`python`. Any version that supports JSR-223 and Java 8 should work.

```yaml
configuration:
  dependencies:
  - "mvn://org.python:jython-standalone:2.7.2"

flows:
  default:
  - script: python
    body: |
      x = 2 * 3;
      execution.setVariable("result", x)

  - log: ${result}
```

Note that `pip` and 3rd-party modules with native dependencies are not
supported.

### Compatibility

Python objects must be converted to regular Java `List` and `Map` instances to be
compatible with the Concord runtime:

```yaml
flows:
  default:
    - script: python
      body: |
        from java.util import HashMap, ArrayList

        aDict = {'x': 123}
        aList = [1, 2, 3]

        execution.setVariable('aDict', HashMap(aDict))
        execution.setVariable('aList', ArrayList(aList))

    - log: "${aDict}"
    - log: "${aList}"
```

## Ruby

Ruby scripts can be executed using the [JRuby](https://www.jruby.org)
runtime. It requires the addition of a dependency to
[jruby](https://repo1.maven.org/maven2/org/jruby/jruby)
located in the Central Repository or on another server and the identifier
`ruby`.

```yaml
configuration:
  dependencies:
  - "mvn://org.jruby:jruby:9.4.2.0"

flows:
  default:
  - script: ruby
    body: |
      puts "Hello!"
```
