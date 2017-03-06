# Processes

## Payload format

The server expects a ZIP archive of the following structure:
- `_main.json` - request data in JSON format (see below);
- `processes` - directory containing `.yml` process definitions.

Anything else will be unpacked as is and will be available for running
processes. The plugins may require other files to be present in a
payload.

The request's JSON format:
```json
{
  "entryPoint": "...",
  "arguments": {
    "a": 1,
    "b": "two",
    "c": false,
    "d": ["x", "y"]
  }
}
```

Only the `entryPoint` parameter is mandatory.

## Starting a new process instance

*TBD*

See also: the [process](./api/process.md) API endpoint.
