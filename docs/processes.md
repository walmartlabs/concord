# Processes

## Authentication

Depending on the configuration of the server, you may need an API key.
The key must be passed in the `Authorization` header on every API request. For example:
```
curl -v -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" ...
```

## Payload format

The server expects a ZIP archive of the following structure:
- `_main.json` - request data in JSON format (see below);
- `processes` - directory containing `.yml` process definitions.

Anything else will be unpacked as is and will be available for running processes.

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

To start a new process instance, send a POST requests containing a payload to Concord's API endpoint:
```
curl -v -H "Content-Type: application/octet-stream" --data-binary @request.zip http://host:port/api/v1/process
```

An example of a response:
```
HTTP/1.1 200 OK
Content-Type: application/json
{
  "instanceId" : "a13f153d-f560-400f-a030-78ddb00738e0"
}
```

## Starting a new process instance using a project

*TBD*

## Waiting for completion

You can wait for completion of a process using this query:
```
curl ... 'http://host:port/api/v1/process/a13f153d-f560-400f-a030-78ddb00738e0/wait?timeout=10000'
```

(Note the quotes, they are probably required by used shell interpreter).

The response will be sent when a process reaches one of the terminal states: `FINISHED` or `FAILED`.

The (optional) `timeout` parameter makes the query wait for the specified amount of time (in ms).
If a process fails to complete in a specified amount of time, an error will be returned.

## Checking the status of a process

To check the status of the started process, use the ID from the response in the status retrieval request:
```
curl -v http://localhost:8001/api/v1/process/a13f153d-f560-400f-a030-78ddb00738e0
```

This returns the current status of the process:
```
HTTP/1.1 200 OK
Content-Type: application/json
{
  "status" : "RUNNING"
}
```

## Stopping a running process

To immediately stop a running process:
```
curl -v -X DELETE http://localhost:8001/api/v1/process/a13f153d-f560-400f-a030-78ddb00738e0
```

**Warning:** no additional external resource cleanup is performed.
