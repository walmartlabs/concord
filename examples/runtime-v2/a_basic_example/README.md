# About Runtime V2

Concord runtime-v2 provides a process execution model with better
performance, error handling, and user experience.

Runtime-v2 needs changes to flows and plugins. Therefore, v1 and v2 flows
can run in the same Concord installation.

To enable runtime-v2, add this configuration to your `concord.yml` file:

```yaml
configuration:
  runtime: "concord-v2"
```

Set the runtime parameter in the API request:
```
$ curl ... -F runtime=concord-v2 http://concord.example.com/api/v1/process
```

For more information, see the [runtime-v2 overview](https://concord.walmartlabs.com/docs/processes-v2/index.html) and the
[migration guide](https://concord.walmartlabs.com/docs/processes-v2/migration.html).
