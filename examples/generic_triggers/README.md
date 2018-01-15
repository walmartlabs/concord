# Example: Using Generic Event Trigger

The example shows how to subscribe to generic events.

## Usage

Create a new project with a repository pointing to this example and
send a JSON request with the event's body:
```
curl -v -u username -H 'Content-Type: application/json' \
-d '{"myVar": "abc", "otherStuff": [1, 2, 3]}' \
http://localhost:8001/api/v1/events/mySystem
``` 

The process log should contain a record similar to this:
```
[INFO ] c.w.concord.plugins.log.LoggingTask - Received {myVar=abc, otherStuff=[1, 2, 3]}
```

Send another request to test the second trigger:
```
curl -v -u username -H 'Content-Type: application/json' \
-d '{"myVar": "testing stuff"}' \
http://localhost:8001/api/v1/events/mySystem
```