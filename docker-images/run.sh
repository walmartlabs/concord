#!/bin/bash

docker rm -f agent server console

docker run -d -p 8002:8002 \
--name agent \
walmartlabs/concord-agent

docker run -d -p 8001:8001 \
--name server \
--link agent \
walmartlabs/concord-server

docker run -d -p 8080:8080 \
--name console \
--link server \
-v /opt/concord/console/landing:/opt/concord/console/landing:ro \
walmartlabs/concord-console
