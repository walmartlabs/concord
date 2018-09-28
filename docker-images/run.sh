#!/bin/bash

VERSION="0.88.2"
BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ -z "$CONCORD_CFG_FILE" ]; then
    CONCORD_CFG_FILE="${BASE_DIR}/server.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

docker rm -f db dind agent server console

docker run -d \
--name db \
-e "POSTGRES_PASSWORD=q1" \
hub.docker.prod.walmart.com/library/postgres:10.4-alpine

docker pull docker.prod.walmart.com/walmartlabs/concord-server:${VERSION}
docker run -d \
-p 8001:8001 \
--name server \
--link db \
-v "${CONCORD_CFG_FILE}:/opt/concord/conf/server.conf:ro" \
-e "DB_URL=jdbc:postgresql://db:5432/postgres" \
-e "CONCORD_CFG_FILE=/opt/concord/conf/server.conf" \
docker.prod.walmart.com/walmartlabs/concord-server:${VERSION}

docker pull docker.prod.walmart.com/walmartlabs/concord-dind
docker run -d \
--privileged \
--name dind \
-v /tmp:/tmp \
docker.prod.walmart.com/walmartlabs/concord-dind

docker pull docker.prod.walmart.com/walmartlabs/concord-agent:${VERSION}
docker run -d \
--name agent \
--link dind \
--link server \
-v /tmp:/tmp \
-v "${HOME}/.m2/repository:/home/concord/.m2/repository" \
-v "${BASE_DIR}/mvn.json:/opt/concord/conf/mvn.json:ro" \
-e "CONCORD_MAVEN_CFG=/opt/concord/conf/mvn.json" \
-e "CONCORD_DOCKER_LOCAL_MODE=false" \
-e "SERVER_API_BASE_URL=http://server:8001" \
docker.prod.walmart.com/walmartlabs/concord-agent:${VERSION}

docker pull docker.prod.walmart.com/walmartlabs/concord-console:${VERSION}
docker run -d \
-p 8080:8080 \
--name console \
--link server \
docker.prod.walmart.com/walmartlabs/concord-console:${VERSION}
