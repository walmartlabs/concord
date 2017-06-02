#!/bin/bash

docker rm -f console &> /dev/null

docker run -it --rm \
--network=host \
--name console \
-e SSL=true \
-e SSL_CERT_PATH=/opt/concord/ssl \
-e SERVER_PORT_8001_TCP_ADDR=localhost \
-e SERVER_PORT_8001_TCP_PORT=8001 \
-v /opt/concord/console/landing:/opt/concord/console/landing:ro \
-v /opt/concord/ssl:/opt/concord/ssl:ro \
walmartlabs/concord-console
