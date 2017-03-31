# Processes

## Payload format

The server expects a ZIP archive of the following structure:
- `_main.json` - request data in JSON format (see below);
- `_defaults.json` - request's default in the same format as `_main.json`.
  Before sending the payload to an agent, the values from `_default.json` and
  `_main.json` will be merged. This allows users to set some default values
  for every request by, for example, storing `_defaults.json` in a GIT repository
  or in a project template;
- `processes` - directory containing `.yml` process definitions;
- `lib` - directory for additional runtime dependencies.

Anything else will be unpacked as is and will be available for running
processes. The plugins may require other files to be present in a
payload.

The request's JSON format:
```json
{
  "entryPoint": "...",
  "dependencies": [ ... ],
  "arguments": {
    "a": 1,
    "b": "two",
    "c": false,
    "d": ["x", "y"]
  }
}
```

Only the `entryPoint` parameter is mandatory.

The `dependencies` array allows to pull external dependencies necessary
for the process' execution. Each element of the array must be a valid URL.
Dependencies are resolved by the agent, before starting a process.

## Starting a new process instance

*TBD*

See also: the [process](./api/process.md) API endpoint.
