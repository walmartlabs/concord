# Tasks

- [Using Tasks](#use-task)
- [Development](#development)
  - [Creating Tasks](#create-task)
  - [Using External Artifacts](#using-external-artifacts)
  - [Best Practices](#best-practices)

<a name="use-task"/>

## Using Tasks

In order to be able to use a task a URL to the JAR containing the implementation
has to be added as a [dependency](../processes-v1/configuration.md#dependencies).
Typically, the JAR is published to a repository manager and a URL pointing to
the JAR in the repository is used.

You can invoke a task via an expression or with the `task` step type.

Following are a number of examples:

```yaml
configuration:
  dependencies:
    - "http://repo.example.com/myConcordTask.jar"
flows:
  default:
    # invoking via usage of an expression and the call method
    - ${myTask.call("hello")}

    # calling a method with a single argument
    - myTask: hello

    # calling a method with a single argument
    # the value will be a result of expression evaluation
    - myTask: ${myMessage}

    # calling a method with two arguments
    # same as ${myTask.call("warn", "hello")}
    - myTask: ["warn", "hello"]

    # calling a method with a single argument
    # the value will be converted into Map<String, Object>
    - myTask: { "urgency": "high", message: "hello" }

    # multiline strings and string interpolation is also supported
    - myTask: |
        those line breaks will be
        preserved. Here will be a ${result} of EL evaluation.
```

If a task implements the `#execute(Context)` method, some additional
features like in/out variables mapping can be used:

```yaml
flows:
  default:
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

## Development

<a name="create-task"/>

### Creating Tasks

Tasks must implement `com.walmartlabs.concord.sdk.Task` Java interface.

The Task interface is provided by the `concord-sdk` module:

```xml
<dependency>
  <groupId>com.walmartlabs.concord</groupId>
  <artifactId>concord-sdk</artifactId>
  <version>{{ site.concord_core_version }}</version>
  <scope>provided</scope>
</dependency>
```

Some dependencies are provided by the runtime. It is recommended to mark them
as `provided` in the POM file:
- `com.fasterxml.jackson.core/*`
- `javax.inject/javax.inject`
- `org.slf4j/slf4j-api`

Here's an example of a simple task:

```java
import com.walmartlabs.concord.sdk.Task;
import javax.inject.Named;

@Named("myTask")
public class MyTask implements Task {

    public void sayHello(String name) {
        System.out.println("Hello, " + name + "!");
    }

    public int sum(int a, int b) {
        return a + b;
    }
}
```

This task can be called using an [expression](../processes-v1/flows.md#expressions)
in short or long form:

```yaml
flows:
  default:
  - ${myTask.sayHello("world")}         # short form

  - expr: ${myTask.sum(1, 2)}           # full form
    out: mySum
    error:
    - log: "Wham! ${lastError.message}"
```

If a task implements `Task#execute` method, it can be started using
`task` step type:

```java
import com.walmartlabs.concord.sdk.Task;
import com.walmartlabs.concord.sdk.Context;
import javax.inject.Named;

@Named("myTask")
public class MyTask implements Task {

    @Override
    public void execute(Context ctx) throws Exception {
        System.out.println("Hello, " + ctx.getVariable("name"));
        ctx.setVariable("success", true);
    }
}
```

```yaml
flows:
  default:
  - task: myTask
    in:
      name: world
    out:
      success: callSuccess
    error:
      - log: "Something bad happened: ${lastError}"
```

This form allows use of `in` and `out` variables and error-handling blocks.

The `task` syntax is recommended for most use cases, especially when dealing
with multiple input parameters.

If a task contains method `call` with one or more arguments, it can
be called using the _short_ form:

```java
import com.walmartlabs.concord.common.Task;
import javax.inject.Named;

@Named("myTask")
public class MyTask implements Task {

    public void call(String name, String place) {
        System.out.println("Hello, " + name + ". Welcome to " + place);
    }
}
```

```yaml
flows:
  default:
  - myTask: ["user", "Concord"]   # using an inline YAML array

  - myTask:                       # using a regular YAML array
    - "user"
    - "Concord"
```

Context variables can be automatically injected into task fields or
method arguments:

```java
import com.walmartlabs.concord.common.Task;
import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.sdk.Context;
import javax.inject.Named;

@Named("myTask")
public class MyTask implements Task {

    @InjectVariable("context")
    private Context ctx;

    public void sayHello(@InjectVariable("greeting") String greeting, String name) {
        String s = String.format(greeting, name);
        System.out.println(s);

        ctx.setVariable("success", true);
    }
}
```

```yaml
flows:
  default:
  - ${myTask.sayHello("Concord")}

configuration:
  arguments:
    greeting: "Hello, %s!"
```

### Using External Artifacts

The runtime provides a way for tasks to download and cache external artifacts:
```java
import com.walmartlabs.concord.sdk.DependencyManager;

@Named("myTask")
public class MyTask implements Task {
    
    private final DependencyManager dependencyManager;
    
    @Inject
    public MyTask(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }
    
    @Override
    public void execute(Context ctx) throws Exception {
        URI uri = ...
        Path p = dependencyManager.resolve(uri);
        // ...do something with the returned path
    }
}
```

The `DependencyManager` is an `@Inject`-able service that takes care of
resolving, downloading and caching URLs. It supports all URL types as
the regular [dependencies](../processes-v1/configuration.md#dependencies)
section in Concord YAML files - `http(s)`, `mvn`, etc.

Typically, cached copies are persistent between process executions (depends on
the Concord's environment configuration).

The tasks shouldn't expect the returning path to be writable (i.e. read-only
access).

`DependencyManager` shouldn't be used as a way to download deployment
artifacts. It's not a replacement for [Ansible]({{ site.concord_plugins_v1_docs }}/ansible.md) or any
other deployment tool.

<a name="best-practices"/>

### Best Practices

Here are some of the best practices when creating a new plugin with one or
multiple tasks.

#### Environment Defaults

Instead of hard coding parameters like endpoint URLs, credentials and other
environment-specific values, use injectable defaults:

```java
@Named("myTask")
public class MyTask implements Task {

    @Override
    public void execute(Context ctx) throws Exception {
        Map<String, Object> defaults = ctx.getVariable("myTaskDefaults");

        String value = (String) ctx.getVariable("myVar");
        if (value == null) {
            // fallback to the default value
            value = (String) defaults.get("myVar");
        }
        System.out.println("Got " + value);
    }
}
```

The environment-specific defaults are provided using
the [Default Process Variables](../getting-started/configuration.md#default-process-variables)
file.

The task's default can also be injected using `@InjectVariable`
annotation - check out the [GitHub task]({{ site.concord_plugins_source }}blob/master/tasks/git/src/main/java/com/walmartlabs/concord/plugins/git/v1/GitHubTaskV1.java#L37-L38)
as the example.

#### Full Syntax vs Expressions

There are two ways how the task can be invoked: the `task`  syntax and
using expressions. Consider the `task` syntax for tasks with multiple
parameters and expressions for tasks that return data and should be used inline:

```yaml
# use the `task` syntax when you need to pass multiple parameters and/or complex data structures
- task: myTask
  in:
    param1: 123
    param2: "abc"
    nestedParams:
      x: true
      y: false
      
# use expressions for tasks returning data
- log: "${myTask.getAListOfThings()}"
```

#### Task Output and Error Handling

Consider storing the task's results in a `result` variable of the following
structure:

Successful execution:

```yaml
result:
  ok: true
  data: "the task's output"  
```

Failed execution:

```yaml
result:
  ok: false  
  errorCode: 404
  error: "Not found"  
```

The `ok` parameter allows users to quickly test whether the execution was
successful or not:

```yaml
- task: myTask

- if: ${!result.ok}
  then:
    - throw: "Something went wrong: ${result.error}"
```

By default the task should throw an exception in case of any execution errors
or invalid input parameters. Consider adding the `ignoreErrors` parameter to
catch all execution errors, but not the invalid arguments errors. Store
the appropriate error message and/or the error code in the `result` variable:

Throw an exception:

```yaml
- task: myTask
  in:
    url: "https://httpstat.us/404"
```

Save the error in the `result` variable:

```yaml
- task: myTask
  in:
    url: "https://httpstat.us/404"
    ignoreErrors: true

- log: "${result.errorCode}"
```

Use the standard JRE classes in the task's results. Custom types can cause
serialization issues when the process suspends, e.g. on a [form](../getting-started/forms.md)
call. If you need to return some complex data structure, consider converting it
to regular Java collections. The runtime provides
[Jackson](https://github.com/FasterXML/jackson) as the default JSON/YAML library
which can also be used to convert arbitrary data classes into regular Map's and
List's:

```java
import com.fasterxml.jackson.databind.ObjectMapper;

@Named("myTask")
public class MyTask implements Task {

    @Override
    public void execute(Context ctx) throws Exception {
        MyResult result = new MyResult();
        ObjectMapper om = new ObjectMapper();
        ctx.setVariable("result", om.convertValue(result, Map.class));
    }

    public static class MyResult implements Serializable {
        boolean ok;
        String data;
    }
}
```

#### Unit Tests

Consider using unit tests to quickly test the task without publishing SNAPSHOT
versions. Use a library like [Mockito](https://site.mockito.org/) to replace
the dependencies in your task with "mocks":

```java
@Test
public void test() throws Exception {
    SomeService someService = mock(SomeService.class);

    Map<String, Object> params = new HashMap();
    params.put("url", "https://httpstat.us/404");
    Context ctx = new MockContext(params);

    MyTask t = new MyTask(someService);
    t.execute(ctx);

    assertNotNull(ctx.getVariable("result"));
}
```

#### Integration Tests

It is possible to test a task using a running Concord instance without
publishing the task's JAR. Concord automatically adds `lib/*.jar` files from
[the payload archive](../api/process.md#zip-file) to the process'
classpath. This mechanism can be used to upload local JAR files and,
consequently, to test locally-built JARs. Check out the
[custom_task]({{ site.concord_source }}/tree/master/examples/custom_task)
example. It uses Maven to collect all `compile` dependencies of the task
and creates a payload archive with the dependencies and the task's JAR.

**Note:** It is important to use `provided` scope for the dependencies that are
already included in the runtime. See [Creating Tasks](#create-task) section for
the list of provided dependencies.
