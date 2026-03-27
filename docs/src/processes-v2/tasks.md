# Tasks

- [Using Tasks](#using-tasks)
- [Full Syntax vs Expressions](#full-syntax-vs-expressions)
- [Development](#development)
  - [Complete Example](#complete-example)
  - [Creating Tasks](#creating-tasks)
  - [Dry-run mode](dry-run-mode)
  - [Task Output](#task-output)
  - [Injectable Services](#injectable-services)
  - [Call Context](#call-context)
  - [Using External Artifacts](#using-external-artifacts)
  - [Environment Defaults](#environment-defaults)
  - [Task Output and Error Handling](#task-output)
  - [Unit Tests](#unit-tests)
  - [Integration Tests](#integration-tests)

## Using Tasks

In order to be able to use a task a URL to the JAR containing
the implementation has to be added as a
[dependency](./configuration.md#dependencies).

Typically, the JAR is published to a Maven repository or a remote host and a
URL pointing to the JAR in the repository is used.

You can invoke tasks in multiple ways. Following are a number of examples,
check the [Task Calls](./flows.md#task-calls) section for more details:

```yaml
configuration:
  dependencies:
    - "http://repo.example.com/my-concord-task.jar"

flows:
  default:
    # call methods directly using expressions
    - ${myTask.call("hello")}

    # call the task using "task" syntax
    # use "out" to save the task's output and "error" to handle errors
    - task: myTask
      in:
        taskVar: ${processVar}
        anotherTaskVar: "a literal value"
      out: myResult
      error:
        - log: myTask failed with ${lastError}
```

## Full Syntax vs Expressions

There are two ways how the task can be invoked: the `task`  syntax and
using expressions. Consider the `task` syntax for tasks with multiple
parameters. Use expressions for simple tasks that return data:

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

## Development

We recommend running Concord using Java 17.

### Complete Example

Check out the [hello-world-task](https://github.com/concord-workflow/hello-world-task)
project for a complete example of a Concord task including end to end testing
using [testcontainers-concord](https://github.com/concord-workflow/testcontainers-concord).

### Creating Tasks

Tasks must implement `com.walmartlabs.concord.runtime.v2.sdk.Task` Java
interface and must be annotated with `javax.inject.Named`.

The following section describes the necessary Maven project setup steps.

Add `concord-targetplatform` to your `dependencyManagement` section:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.walmartlabs.concord</groupId>
      <artifactId>concord-targetplatform</artifactId>
      <version>{{site.concord_core_version}}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Add the following dependencies to your `pom.xml`:

```xml
<dependencies>
  <dependency>
    <groupId>com.walmartlabs.concord.runtime.v2</groupId>
    <artifactId>concord-runtime-sdk-v2</artifactId>
    <version>{{site.concord_core_version}}</version>
    <scope>provided</scope>
  </dependency>

  <dependency>
    <groupId>javax.inject</groupId>
    <artifactId>javax.inject</artifactId>
    <version>1</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

Add `sisu-maven-plugin` to the `build` section:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.eclipse.sisu</groupId>
      <artifactId>sisu-maven-plugin</artifactId>
    </plugin>
  </plugins>
</build>
```

Some dependencies are provided by the runtime. It is recommended to mark such
dependencies as `provided` in the POM file to avoid classpath conflicts:
- `com.fasterxml.jackson.core/*`
- `javax.inject/javax.inject`
- `org.slf4j/slf4j-api`

Implement the `com.walmartlabs.concord.runtime.v2.sdk.Task` interface and add
`javax.inject.Named` annotation with the name of the task.

Here's an example of a simple task:

```java
import com.walmartlabs.concord.runtime.v2.sdk.*;
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

This task can be called using an [expression](./flows.md#expressions):
```yaml
flows:
  default:
  - ${myTask.sayHello("world")}         # short form

  - expr: ${myTask.sum(1, 2)}           # full form
    out: mySum
```

If a task implements `Task#execute` method, it can be started using
`task` step type:

```java
@Named("myTask")
public class MyTask implements Task {

    @Override
    public TaskResult execute(Variables input) throws Exception {
        String name = input.assertString("name");
        return TaskResult.success()
                    .value("msg", "Hello, " + name + "!");
    }
}
```

The task receives a `Variables` object as input. It contains all `in`
parameters of the call and provides some utility methods to validate
the presence of required parameters, convert between types, etc.

Tasks can use the `TaskResult` object to return data to the flow. See
the [Task Output](#task-output) section for more details.

To call a task with an `execute` method, use the `task` syntax:

```yaml
flows:
  default:
    - task: myTask
      in:
        name: "world"
      out: myResult

    - log: "${myResult.msg}" # prints out "Hello, world!"
```

This form allows use of `in` and `out` variables and error-handling blocks.
See the [Task Call](./flows.md#task-calls) section for more details.

In the example above, the task's result is saved as `myResult` variable.
The runtime converts the `TaskResult` object into a regular Java `Map` object:

```json
{
  "ok": true,
  "msg": "Hello, world!"
}
```

The `ok` value depends on whether the result was constructed as
`TaskResult#success()` or `TaskResult#error(String)`. In the latter case,
the resulting object also contains an `error` key with the specified error
message.

The `task` syntax is recommended for most use cases, especially when dealing
with multiple input parameters.

### Dry-run mode

[Dry-run mode](../processes-v2/index.md#dry-run-mode) is useful for testing and validating
the flow and task logic before running it in production.

To mark a task as ready for execution in dry-run mode, you need to annotate the task with
`com.walmartlabs.concord.runtime.v2.sdk.DryRunReady` annotation:

```java
@DryRunReady
@Named("myTask")
public class MyTask implements Task {

    @Override
    public TaskResult execute(Variables input) throws Exception {
        String name = input.assertString("name");
        return TaskResult.success()
                    .value("msg", "Hello, " + name + "!");
    }
}
```

If you need to change the logic in the task depending on whether it is running in dry-run mode or not,
you can use the `context.processConfiguration().dryRun()`. it indicate whether the process is running
in dry-run mode:

```java
@DryRunReady
@Named("myTask")
public class MyTask implements Task {

    private final boolean dryRunMode;
    
    @Inject
    public MyTask(Context context) {
        this.dyrRunMode = context.processConfiguration().dryRun();
    }
    
    @Override
    public TaskResult execute(Variables input) throws Exception {
        if (dryRunMode) {
            return TaskResult.success();        
        }
        
        // here is the logic that can't be executed in dry-run mode
        // ...
    }
}
```

### Task Output

The task must return a `TaskResult` instance. The `TaskResult` class
provides methods to return additional values as the task call's result. A task
can return multiple values:

```java
return TaskResult.success()
    .value("foo", "bar")
    .value("baz", 123);
```

Values of any type can be returned, but we recommend returning standard JDK
types. Preferably `Serializable` to avoid serialization issues (e.g. when
using [forms](../getting-started/forms.md)).

If you need to return some complex data structure, consider converting it
to regular Java collections. The runtime provides
[Jackson](https://github.com/FasterXML/jackson) as the default JSON/YAML library
which can also be used to convert arbitrary data classes into regular Map's and
List's:

```java
import com.fasterxml.jackson.databind.ObjectMapper;

@Named("myTask")
public class MyTask implements Task {

    @Override
    public TaskResult execute(Variables input) throws Exception {
        MyResult result = new MyResult();
        ObjectMapper om = new ObjectMapper();
        return TaskResult.success()
                .values(om.convertValue(result, Map.class));
    }

    public static class MyResult implements Serializable {
        String data;
        List<String> stuff;
    }
}
```

In the example above, the properties of `MyResult` instance became values in
the result Map:

```yaml
- task: myTask
  out: result

- log: |
    data = ${result.data}
    stuff = ${result.stuff}
```

### Injectable Services

The SDK provides a number of services that can be injected into task
classes using the `javax.inject.Inject` annotation:

- `Context` - provides access to the current call's environment, low-level
  access to the runtime, etc. See the [Call Context](#call-context) section for
  more details;
- `DependencyManager` - a common way for tasks to work with external
  dependencies. See the [Using External Artifacts](#using-external-artifacts)
  section for details.
  
### Call Context

To access the current task call's environment,
`com.walmartlabs.concord.runtime.v2.sdk.Context` can be injected into the task
class:

```java
@Named("myTask")
public class MyTask implements Task {

    private final Context ctx;

    @Inject
    public MyTask(Context ctx) {
        this.ctx = ctx;
    }
}
```

The `Context` object provides access to multiple features, such as:

- `workingDirectory()` - returns `Path`, the working directory of the current
  process;
- `processInstanceId()` - returns `UUID`, the current process' unique
  indentifier;
- `variables()` - provides access to the current flow's `Variables`, i.e. all
  variables defined before the current task call;
- `defaultVariables()` - default input parameters for the current task. See
  the [Environment Defaults](#environment-defaults) section for more details.

For the complete list of provided features please refer to Javadoc of
the `Context` interface.

### Using External Artifacts

The runtime provides a way for tasks to download and cache external artifacts:
```java
import com.walmartlabs.concord.runtime.v2.sdk.*;

@Named("myTask")
public class MyTask implements Task {
    
    private final DependencyManager dependencyManager;
    
    @Inject
    public MyTask(DependencyManager dependencyManager) {
        this.dependencyManager = dependencyManager;
    }
    
    @Override
    public TaskResult execute(Variables input) throws Exception {
        URI uri = ...
        Path p = dependencyManager.resolve(uri);
        // ...do something with the returned path
    }
}
```

The `DependencyManager` is an `@Inject`-able service that takes care of
resolving, downloading and caching URLs. It supports all URL types as
the regular [dependencies](./configuration.md#dependencies) section in
Concord YAML files - `http(s)`, `mvn`, etc.

Typically, cached copies are persistent between process executions (depends on
the Concord's environment configuration).

The tasks shouldn't expect the returning path to be writable (i.e. assume only
read-only access).

`DependencyManager` shouldn't be used as a way to download deployment
artifacts. It's not a replacement for [Ansible]({{ site.concord_plugins_v1_docs }}/ansible.md) or any
other deployment tool.

### Environment Defaults

Instead of hard coding parameters like endpoint URLs, credentials and other
environment-specific values, use `Context#defaultVariables`:

```java
import com.walmartlabs.concord.runtime.v2.sdk.*;

@Named("myTask")
public class MyTask implements Task {

    private final Context ctx;

    @Inject
    public MyTask(Context ctx) {
        this.ctx = ctx;
    }
    
    @Override
    public TaskResult execute(Variables input) throws Exception {
        Map<String, Object> defaults = ctx.defaultVariables().toMap();
        ...
    }
}
```

The environment-specific defaults are provided using a
[Default Process Configuration Rule](../getting-started/policies.md#default-process-configuration-rule)
policy. A `defaultTaskVariables` entry matching the plugin's `@Named` value is
provided to the plugin at runtime via the `ctx.defaultVariables()` method.

```json
{
  "defaultProcessCfg": {
    "defaultTaskVariables": {
      "github": {
        "apiUrl": "https://github.example.com/api/v3"
      }
    }
  }
}
```

Check out the
[GitHub task]({{site.concord_plugins_source}}blob/master/tasks/git/src/main/java/com/walmartlabs/concord/plugins/git/v2/GithubTaskV2.java#L43)
as the example.

### Error Handling

By default, the task should throw an exception in case of any execution errors
or invalid input parameters. Consider adding the `ignoreErrors` parameter to
catch all execution errors except for input validation errors.

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
  out: result

- log: "${result.errorCode}"
```

### Unit Tests

Consider using unit tests to quickly test the task without publishing SNAPSHOT
versions. Use a library like [Mockito](https://site.mockito.org/) to replace
the dependencies in your task with "mocks":

```java
@Test
public void test() throws Exception {
    Map<String, Object> input = new HashMap();
    input.put("name", "Concord");
    
    MyTask t = new MyTask(someService);
    TaskResult.SimpleResult result = (TaskResult.SimpleResult) t.execute(new MapBackedVariables(input));

    assertEquals("Hello, Concord", result.toMap().get("msg"));
}
```

### Integration Tests

The [testcontainers-concord](https://github.com/concord-workflow/testcontainers-concord)
project provides a JUnit4 test rule to run Concord in Docker. See
[the complete example](#complete-example) for more details.

Alternatively, it is possible to test a task using a running Concord instance
without publishing the task's JAR. Concord automatically adds `lib/*.jar` files
from [the payload archive](../api/process.md#zip-file) to the process'
classpath. This mechanism can be used to upload local JAR files and,
consequently, to test locally-built JARs. Check out the
[custom_task]({{ site.concord_source }}/tree/master/examples/custom_task)
example. It uses Maven to collect all `compile` dependencies of the task
and creates a payload archive with the dependencies and the task's JAR.

**Note:** It is important to use `provided` scope for the dependencies that are
already included in the runtime. See [Creating Tasks](#create-task) section for
the list of provided dependencies.
