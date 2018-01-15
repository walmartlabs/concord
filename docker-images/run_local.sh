#!/bin/bash

if [ -z $LDAP_PROPERTIES ]; then
    LDAP_PROPERTIES="/opt/concord/conf/ldap.properties"
fi
echo "LDAP_PROPERTIES: ${LDAP_PROPERTIES}"

docker rm -f db dind agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
-e 'PGDATA=/var/lib/postgresql/data/pgdata' \
--mount source=concordDB,target=/var/lib/postgresql/data \
-p 5432:5432 \
--network=host \
hub.docker.prod.walmart.com/library/postgres:latest

docker run -d \
--name server \
-v /tmp:/tmp \
-v ${HOME}:${HOME}:ro \
-v ${LDAP_PROPERTIES}:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://localhost:5432/postgres' \
--network=host \
walmartlabs/concord-server

docker run -d \
--name agent \
-v /tmp:/tmp \
-v ${HOME}:${HOME}:ro \
-v ${HOME}/.m2/repository:/root/.m2/repository:ro \
-e 'DOCKER_HOST=tcp://localhost:2375' \
--network=host \
walmartlabs/concord-agent

docker run -d \
--name console \
-e 'SERVER_ADDR=localhost' \
-e 'SERVER_PORT=8001' \
--network=host \
walmartlabs/concord-console
