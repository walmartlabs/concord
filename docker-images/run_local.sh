#!/bin/bash

docker rm -f db dind agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
--network=host \
hub.docker.prod.walmart.com/library/postgres:latest

docker run -d \
--name server \
-v /tmp:/tmp \
-v /opt/concord/conf/ldap.properties:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://localhost:5432/postgres' \
--network=host \
walmartlabs/concord-server

docker run -d \
--privileged \
--name dind \
--network=host \
docker.prod.walmart.com/walmartlabs/concord-dind

docker run -d \
--name agent \
-v /tmp:/tmp \
-e 'DOCKER_HOST=tcp://localhost:2375' \
--network=host \
walmartlabs/concord-agent

docker run -d \
--name console \
--network=host \
walmartlabs/concord-console
