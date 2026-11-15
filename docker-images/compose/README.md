# Running Concord with Docker Compose

This setup requires [Docker Compose](https://docs.docker.com/compose/) version 1.26.x or later.

## Usage

```
$ docker-compose up
```

On the first start, the Server generates the default admin API token. Check the
logs to find the token. Use http://localhost:8001/#/login?useApiKey=true to
open the Concord UI with an API key.
