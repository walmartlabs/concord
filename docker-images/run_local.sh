#!/bin/bash

if [ -z $LDAP_CFG ]; then
    LDAP_CFG="/opt/concord/conf/ldap.properties"
fi
echo "LDAP_PROPERTIES: ${LDAP_CFG}"

docker rm -f db agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
-p 5432:5432 \
library/postgres:latest

docker run -d \
--name server \
-v /tmp:/tmp \
-v ${HOME}:${HOME}:ro \
-v ${HOME}/.m2/repository:/home/concord/.m2/repository:ro \
-v ${LDAP_CFG}:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://localhost:5432/postgres' \
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
