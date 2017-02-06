# PostgreSQL Support

## Using the official docker images

- start the agent:

```
docker run -d -p 8002:8002 --name agent walmartlabs/concord-agent
```

- start the database:

```
docker run -d \
-v /path/to/data:/var/lib/postgresql/data \
--name pg \
-e POSTGRES_PASSWORD=myPgPassword \
postgres
```

- start the server:

```
docker run -d \
-p 8001:8001 \
--name server \
--link agent \
--link pg
-e DB=postgresql \
-e DB_PASSWORD=myPgPassword \
walmartlabs/concord-server
```