# PostgreSQL Support

## Using the official docker images

- start the agent:

```
docker run -d -p 8002:8002 --name agent walmartlabs/concord-agent
```

- start the database:

```
docker run -d \
-p 127.0.0.1:5432:5432 \
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
-e DB_DIALECT=POSTGRES_9_5 \
-e DB_DRIVER=org.postgresql.Driver \
-e DB_URL=jdbc:postgresql://localhost:5432/postgres \
-e DB_PASSWORD=myPgPassword \
walmartlabs/concord-server
```