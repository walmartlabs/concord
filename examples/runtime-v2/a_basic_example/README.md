# About Runtime V2

Concord's runtime-v2 provides a process execution model with improved
performance, error handling, and user experience.

Runtime-v2 features require changes in flows and plugins, so v1 and v2 flows
can coexist in the same Concord installation.

To enable the v2 runtime, add the following to your concord.yml file:
```yaml
configuration:
  runtime: "concord-v2"
```

Alternatively, it is possible to specify the runtime parameter’s value in the API request:
```
$ curl ... -F runtime=concord-v2 http://concord.example.com/api/v1/process
```

Learn more using the [runtime-v2 overview](https://concord.walmartlabs.com/docs/processes-v2/index.html) and the
[migration guide](https://concord.walmartlabs.com/docs/processes-v2/migration.html) docs.
