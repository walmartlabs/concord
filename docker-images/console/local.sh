#!/bin/bash

docker rm -f console

docker run -it --rm \
--network=host \
--name console \
-e SERVER_PORT_8001_TCP_ADDR=localhost \
-e SERVER_PORT_8001_TCP_PORT=8001 \
-v /opt/concord/console/landing:/opt/concord/console/landing:ro \
walmartlabs/concord-console
