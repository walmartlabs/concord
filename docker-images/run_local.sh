#!/bin/bash

if [ -z $LDAP_PROPERTIES ]; then
    LDAP_PROPERTIES="/opt/concord/conf/ldap.properties"
fi
echo "LDAP_PROPERTIES: ${LDAP_PROPERTIES}"

docker rm -f db dind agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
--network=host \
hub.docker.prod.walmart.com/library/postgres:latest

docker run -d \
--name server \
-v /tmp:/tmp \
-v ${LDAP_PROPERTIES}:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://localhost:5432/postgres' \
--network=host \
docker.prod.walmart.com/walmartlabs/concord-server

docker run -d \
--privileged \
--name dind \
-v /tmp:/tmp \
--network=host \
docker.prod.walmart.com/walmartlabs/concord-dind

docker run -d \
--name agent \
-v /tmp:/tmp \
-e 'DOCKER_HOST=tcp://localhost:2375' \
--network=host \
docker.prod.walmart.com/walmartlabs/concord-agent

docker run -d \
--name console \
-e 'SERVER_ADDR=localhost' \
-e 'SERVER_PORT=8001' \
--network=host \
docker.prod.walmart.com/walmartlabs/concord-console
