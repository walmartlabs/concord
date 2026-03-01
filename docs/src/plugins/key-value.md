# Key-value

The key value `kv` task provides access to the server's simple string
key-value store. All data is project-scoped e.a. processes only see the values
created by processes of the same project.

This task is provided automatically by Concord.

## Usage

### Setting a Value

Setting a string value:
```yaml
- ${kv.putString("myKey", "myValue")}
```

Setting an integer (64-bit `long`) value:
```yaml
- ${kv.putLong("myKey", 1234)}
```

### Retrieving a Value

Using the OUT syntax of expressions:

```yaml
- expr: ${kv.getString("myKey")}
  out: myVar

- log: "I've got ${myVar}"
```

Using the context:

```yaml
- ${context.setVariable("myVar", kv.getString("myKey"))}
- log: "I've got ${myVar}"
```

In scripts:

```yaml
- script: groovy
  body: |
    def kv = tasks.get("kv")

    def id = kv.inc(execution, "idSeq")
    println("I've got ${id}")
```

The `execution` variable is an alias for [context](https://concord.walmartlabs.com/docs/getting-started/processes.html#provided-variables)
and automatically provided by the runtime for all supported script engines.
Check out [the source code]({{ site.concord_source }}/blob/master/plugins/tasks/kv/src/main/java/com/walmartlabs/concord/plugins/kv/KvTask.java)
for all available public methods.

Integer values can be retrieved in the same way:

```yaml
- log: "I've got ${kv.getLong('myKey')}"
```

### Removing a Value

```yaml
- ${kv.remove("myVar")}
- if: ${kv.getString("myVar") == null}
  then:
    - log: "Ciao, myVar! You won't be missed."
```

### Incrementing a Value

This can be used as a simple sequence number generator.

```yaml
- expr: ${kv.inc("idSeq")}
  out: myId
- log: "We got an ID: ${myId}"
```

**Warning:** the existing string values can't be incremented.
