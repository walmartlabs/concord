#!/bin/bash

if [ -z "$CONCORD_CFG_FILE" ]; then
    CONCORD_CFG_FILE="/opt/concord/conf/server.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

docker rm -f db agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
-p 5432:5432 \
library/postgres:latest

docker run -d \
--name server \
-v ${HOME}:${HOME}:ro \
-v ${HOME}/.m2/repository:/home/concord/.m2/repository:ro \
-v "${CONCORD_CFG_FILE}:${CONCORD_CFG_FILE}:ro" \
-e "CONCORD_CFG_FILE=$CONCORD_CFG_FILE" \
-e 'GC_LOG_DIR=/tmp/server/gc' \
--network=host \
walmartlabs/concord-server

docker run -d \
--name agent \
-v /tmp:/tmp \
-v ${HOME}:${HOME}:ro \
-v ${HOME}/.m2/repository:/home/concord/.m2/repository:ro \
-e 'DOCKER_HOST=tcp://127.0.0.1:2375' \
-e 'CONCORD_DOCKER_LOCAL_MODE=false' \
--network=host \
walmartlabs/concord-agent

docker run -d \
--name console \
-e 'SERVER_ADDR=localhost' \
-e 'SERVER_PORT=8001' \
--network=host \
walmartlabs/concord-console
