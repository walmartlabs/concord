# Extending Concord

Concord provides several extension points.

## Tasks

Tasks are used to call 3rd-party code or to perform something that is too complex to express it with BPMN or YAML
directly.

An example of a simple task:
```java
import com.walmartlabs.concord.common.Task;
import javax.inject.Named;

@Named("myTask")
public class MyTask implements Task {

    public void sayHello(String name) {
        System.out.println("Hello, " + name + "!");
    }
}
```

Tasks such as this example can be called via JUEL expressions in BPMN's `ServiceTask` or YAML's `expr` elements:
```xml
<bpmn2:serviceTask concord:expressionType="expression"
    concord:expression="${myTask.sayHello('world')}"/>
```

```yaml
main:
- expr: ${myTask.sayHello("world")}
```

See also [the description of expressions](yaml/README.md#expressions).

If a task implements the `JavaDelegate` interface, it can be called as a "delegate expression".
Supported only in the BPMN format:
```java
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.JavaDelegate;
import javax.inject.Named;

@Named("myTask")
public class MyDelegateTask implements JavaDelegate, Task {
   
    @Override
    public void execute(ExecutionContext ctx) throws Exception {
        System.out.println("Hello, " + ctx.getVariable("name"));
    }
}
```

```xml
<bpmn2:serviceTask concord:expressionType="delegate"
    concord:expression="${myTask}"/>
```