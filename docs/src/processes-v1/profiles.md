# Profiles

Profiles are named collections of configuration, forms and flows and can be used
to override defaults set in the top-level content of the Concord file. They are
created by inserting a name section in the `profiles` top-level element.

Profile selection is configured when a process is
[executed](../getting-started/processes.md#overview).

For example, if the process below is executed using the `myProfile` profile,
the value of `foo` is `bazz` and appears in the log instead of the default
`bar`:

```yaml
configuration:
  arguments:
    foo: "bar"

profiles:
  myProfile:
    configuration:
      arguments:
        foo: "bazz"
flows:
  default:
  - log: "${foo}"
```

The `activeProfiles` parameter is a list of project file's profiles that is
used to start a process. If not set, a `default` profile is used.

The active profile's configuration is merged with the default values
specified in the top-level `configuration` section. Nested objects are
merged, lists of values are replaced:

```yaml
configuration:
  arguments:
    nested:
      x: 123
      y: "abc"
    aList:
    - "first item"
    - "second item"

profiles:
  myProfile:
    configuration:
      arguments:
        nested:
          y: "cba"
          z: true
        aList:
        - "primer elemento"
        - "segundo elemento"

flows:
  default:
  # Expected next log output: 123 cba true
  - log: "${nested.x} ${nested.y} ${nested.z}"
  # Expected next log output: ["primer elemento", "segundo elemento"]
  - log: "${aList}"
```

Multiple active profiles are merged in the order they are specified in
`activeProfiles` parameter:

```bash
$ curl ... -F activeProfiles=a,b http://concord.example.com/api/v1/process
```

In this example, values from `b` are merged with the result of the merge
of `a` and the default configuration.
