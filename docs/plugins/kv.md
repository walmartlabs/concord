# KV task

This task provides access to the server's simple key-value store.
All data is project-scoped: processes will see only the values that
were made by the processes of the same project.

## Usage

### Setting a value

```yaml
- ${kv.putString("myKey")}
```

### Retrieving a value

Using the OUT syntax of expressions:

```yaml
- expr: ${kv.getString("myKey")}
  out: myVar
  
- log: "I've got ${myVar}"
```

Using the context:

```yaml
- ${execution.setVariable("myVar", kv.getString("myKey"))}
- log: "I've got ${myVar}"
```

In scripts:

```yaml
- script: groovy
  body: |
    def kv = tasks.get("kv");
    
    def id = kv.inc("idSeq");
    println("I've got {id}");
```

### Removing a value

```yaml
- ${kv.remove("myVar")}
- if: ${kv.getString("myVar") == null}
  then:
    - log: "Ciao, myVar! You won't be missed."
```

### Incrementing a value

This can be used as a simple sequence number generator.

```yaml
- expr: ${kv.inc("idSeq")}
  out: myId
- log: "We got an ID: ${myId}"
```

**Warning**: the exising string values can't be incremented.