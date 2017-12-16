#!/bin/bash

VERSION="latest"

docker rm -f db dind agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
hub.docker.prod.walmart.com/library/postgres:latest

docker run -d \
-p 8001:8001 \
--name server \
--link db \
-v /opt/concord/conf/ldap.properties:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://db:5432/postgres' \
docker.prod.walmart.com/walmartlabs/concord-server:${VERSION}

docker run -d \
--privileged \
--name dind \
-v /tmp:/tmp \
docker.prod.walmart.com/walmartlabs/concord-dind

docker run -d \
--name agent \
--link dind \
--link server \
-v /tmp:/tmp \
docker.prod.walmart.com/walmartlabs/concord-agent:${VERSION}

docker run -d \
-p 8080:8080 \
--name console \
--link server \
docker.prod.walmart.com/walmartlabs/concord-console:${VERSION}
