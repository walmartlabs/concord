# Running Concord with Docker Compose

Requires [docker-compose](https://docs.docker.com/compose/) >= 1.26.x.

## Usage

```
$ docker-compose up
```

The Server generates the default admin API token on the first start up - check
the logs. Use http://localhost:8001/#/login?useApiKey=true to access the
Concord UI with an API key.
