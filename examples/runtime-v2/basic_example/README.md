# About Runtime V2

Starting from version 1.57.0, Concord introduces a new runtime for process execution. This runtime v2 allows robust
performance and error handling coupled with improved user experience.

The new runtime’s features requires changes in flows and plugins. That’s why initially it will be an opt-in feature - 
both v1 and v2 versions will coexist for foreseeable future.

Currently, the v2 runtime is considered a “preview” feature and is subject to change.

To enable the v2 runtime, add the following to your concord.yml file:
```yaml
configuration:
  runtime: "concord-v2"
```

Alternatively, it is possible to specify the runtime parameter’s value in the API request:
```
$ curl ... -F runtime=concord-v2 http://concord.example.com/api/v1/process
```

Learn more using the [runtime v2 overview](http://concord.walmart.com/docs/processes-v2/index.html) and the 
[migration guide](http://concord.walmart.com/docs/processes-v2/migration.html) docs.