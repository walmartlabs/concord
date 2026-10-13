# Projects

Projects are a way to organize processes and their configuration. Projects can
be created using [the API](../api/project.md) or using [Console](../console/project.md).

## Configuration

Project-level configuration provides a way to specify
[configuration](../processes-v2/configuration.md) for all
processes executed in the context of the project.

For example, to specify a common default argument for all project processes:

```
$ curl -ikn -X PUT -H 'Content-Type: application/json' \
-d '{"arguments": {"name": "me"}}' \
https://concord.example.com/api/v1/org/MyOrg/project/MyProject/cfg
```

All processes using the `name` variable get the default value:

```yaml
flows:
  default:
    - log: "Hello, ${name}"
```

```
$ curl -ikn -F org=MyOrg -F project=MyProject -F concord.yml=@concord.yml \
https://concord.example.com/api/v1/process
```

```
10:42:00 [INFO ] c.w.concord.plugins.log.LoggingTask - Hello, me
```

Processes can override project defaults by providing their own values for the
variable in the `configuration` object or in the request parameters.

See [the API](../api/project.md#get-project-configuration) documentation for
more details on how to work with project configurations.
