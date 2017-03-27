# Quick start

The fastest way to get a Concord instance up and running is to use the
pre-built Docker images to run all three components of Concord: the
Concord Agent, the Concord Server, and the Concord Console.

- Referencing a private Docker registry

  If you are using a private Docker registry, add its name to an image
  name in the examples below.  For example, if your private docker
  registry is running on docker.prod.walmart.com this command:
 
  ```
  docker run -d -p 8002:8002 -name agent walmartlabs/concord-agent
  ```

  would be run as:

  ```
  docker run -d -p 8002:8002 -name agent \
       docker.prod.walmart.com/walmartlabs/concord-agent
  ```


- Start the agent:

  ```
  docker run -d -p 8002:8002 --name agent walmartlabs/concord-agent
  ```
  
- Start the server:

  ```
  docker run -d -p 8001:8001 --name server --link agent \
  	 walmartlabs/concord-server
  ```

  This will start the server with an in-memory database and temporary
  storage for its working files. Please see the
  [Configuration](./configuration.md) description to configure a more
  permanent storage.

- See if everything is okay by checking the server logs:
  
  ```
  docker logs server
  ```

- Start the console:

  ```
  docker run -d -p 8080:8080 --name console --link server walmartlabs/concord-console
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
  curl -v -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/octet-stream" --data-binary @archive.zip http://localhost:8001/api/v1/process
  ```

- Check the server logs:
  ```
  docker logs server
  ```
  
  If everything went okay, you should see something like this:
  ```
  00:03:24.916 [INFO ] ...ProcessHistoryDao - update ['9d84dbac-9a22-4885-abe3-dd79df40cad5', FINISHED] -> done
  ```

  You can also check the log by opening it in
  [the Concord console](http://localhost:8080/).

  - (Optional) Stop and remove the containers
  ```
  docker rm -f {console,agent,server}
  ```
