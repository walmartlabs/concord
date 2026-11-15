# Example: Using Generic Event Trigger

This example shows how to subscribe to generic events.

## Usage

Create a project. Point the project to this example.
Send a JSON request with the event body:
```
curl -v -u username -H 'Content-Type: application/json' \
-d '{"myVar": "abc", "otherStuff": [1, 2, 3]}' \
http://localhost:8001/api/v1/events/mySystem
```

Check that the process log contains a record like this:
```
[INFO ] c.w.concord.plugins.log.LoggingTask - Received {myVar=abc, otherStuff=[1, 2, 3]}
```

Send another request to test the second trigger:
```
curl -v -u username -H 'Content-Type: application/json' \
-d '{"myVar": "testing stuff"}' \
http://localhost:8001/api/v1/events/mySystem
```
