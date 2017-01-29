# Quick start

(using the pre-build Docker images)

- Start the agent:
```
docker run -d -p 8002:8002 --name agent walmartlabs/concord-agent
```

- Start the server:
```
docker run -d -p 8001:8001 --name server --link agent walmartlabs/concord-server
```

This will start the server with an in-memory database and temporary storage for its working files. Please see the
[Configuration](./configuration.md) description to configure a more permanent storage.

- See if everything is okay by opening [http://localhost:8080](http://localhost:8080) and checking the server logs:
```
docker logs server
```

- Start the console:
```
docker run -d -p 8080:8080 --name server --link server walmartlabs/concord-console
```

- Create a zip archive of the following structure:
  - `_main.json`
  - `processes/main.yml`
JSON file example:
```json
{
  "entryPoint": "main",
  "arguments": {
    "name": "world"
  }
}
```
YAML file:
```yaml
main:
- expr: ${log.info("test", "Hello, ".concat(name))}
```

- Start a new process instance:
```
curl -v -H "Content-Type: application/octet-stream" --data-binary @archive.zip http://localhost:8001/api/v1/process
```

- Check the server logs:
```
docker logs server
```
If everything went okay, you should see something like this:
```
00:03:24.916 [INFO ] ...ProcessHistoryDao - update ['9d84dbac-9a22-4885-abe3-dd79df40cad5', FINISHED] -> done
```

You can also check the log by opening it in [the Concord console](http://localhost:8080/#/history).

- (Optional) Stop and remove the containers
```
docker rm -f {console,agent,server}
```
