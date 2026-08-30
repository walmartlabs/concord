# Linting

The CLI tool supports "linting" of Concord YAML files. It can validate
the syntax of flows and expressions without actually running them.

```bash
concord lint [-v] [target dir]
```

The `lint` command parses and validates Concord YAML files located in the
current directory or directory specified as an argument. It allows to quickly
verify if the [DSL](../processes-v2/index.md#dsl) syntax and the syntax of
expressions are correct.

Currently, it is not possible to verify whether the tasks are correctly called
and/or their parameter types are correct. It is also does not take dynamically
[imported resources](../processes-v2/imports.md) into account.

For example, the following `concord.yml`is missing a closing bracket in the
playbook expression.

```yaml
flows:
  default:
    - task: ansible
      in:
        playbook: "${myPlaybookName"    # forgot to close the bracket
```

Running `concord lint` produces:

```bash
$ concord lint
ERROR: @ [/home/ibodrov/tmp/lint/test/concord.yml] line: 3, col: 13
        Invalid expression in task arguments: "${myPlaybookName" in IN VariableMapping [source=null, sourceExpression=null, sourceValue=${myPlaybookName, target=playbook, interpolateValue=true] Encountered "<EOF>" at line 1, column 16.Was expecting one of: "}" ... "." ... "[" ... ";" ... ">" ... "gt" ... "<" ... "lt" ... ">=" ... "ge" ... "<=" ... "le" ... "==" ... "eq" ... "!=" ... "ne" ... "&&" ... "and" ... "||" ... "or" ... "*" ... "+" ... "-" ... "?" ... "/" ... "div" ... "%" ... "mod" ... "+=" ... "=" ... 
------------------------------------------------------------

Found:
  profiles: 0
  flows: 1
  forms: 0
  triggers: 0
  (not counting dynamically imported resources)

Result: 1 error(s), 0 warning(s)

INVALID
```

The linting feature is in very early development, more validation rules are
added in future releases.

## Running Flows Locally

**Note:** this feature supports only [`concord-v2` flows](../processes-v2/index.md).
The CLI tool forces the `runtime` parameter value to `concord-v2`.

The CLI tool can run Concord flows locally:

```yaml
# concord.yml
flows:
  default:
    - log: "Hello!"
```

```
$ concord run
Starting...
21:23:45.951 [main] Hello!
...done!
```

By default, `concord run` copies all files in the current directory into
a `$PWD/target` directory -- similarly to Maven.

The `concord run` command doesn't use a Concord Server, the flow execution is
purely local. However, if the flow uses external
[dependencies](../processes-v2/configuration.md#dependencies) or
[imports](../processes-v2/imports.md) a working network connection might be
required.

