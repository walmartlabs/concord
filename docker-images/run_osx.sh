#!/bin/bash

if [ -z $LDAP_PROPERTIES ]; then
    LDAP_PROPERTIES="/opt/concord/conf/ldap.properties"
fi
echo "LDAP_PROPERTIES: ${LDAP_PROPERTIES}"

docker rm -f db dind agent server console

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
hub.docker.prod.walmart.com/library/postgres:latest

docker run -d \
--link db \
--name server \
-p 8001:8001 \
-v /tmp:/tmp \
-v ${LDAP_PROPERTIES}:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
-e 'DB_URL=jdbc:postgresql://db:5432/postgres' \
docker.prod.walmart.com/walmartlabs/concord-server

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
docker.prod.walmart.com/walmartlabs/concord-agent

docker run -d \
--name console \
--link server \
-p 8080:8080 \
docker.prod.walmart.com/walmartlabs/concord-console
