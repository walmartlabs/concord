#!/bin/bash

if [ -z $VERSION ]; then
    VERSION="0.92.0"
fi
echo "VERSION: ${VERSION}"

if [ -z $CONCORD_CFG_FILE ]; then
    CONCORD_CFG_FILE="/opt/concord/conf/server.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

echo "Removing old containers..."
docker rm -f db dind agent server console > /dev/null

docker run -d \
--name db \
-e 'POSTGRES_PASSWORD=q1' \
-e 'PGDATA=/var/lib/postgresql/data/pgdata' \
--mount source=concordDB,target=/var/lib/postgresql/data \
-p 5432:5432 \
hub.docker.prod.walmart.com/library/postgres:10.4-alpine

docker run -d \
--link db \
--name server \
-p 8001:8001 \
-v /tmp:/tmp \
-v "${CONCORD_CFG_FILE}:${CONCORD_CFG_FILE}:ro" \
-e "CONCORD_CFG_FILE=${CONCORD_CFG_FILE}" \
-e 'DB_URL=jdbc:postgresql://db:5432/postgres' \
docker.prod.walmart.com/walmartlabs/concord-server:${VERSION}

# wait for the server to start
echo -n "Waiting for the server to start"
until $(curl --output /dev/null --silent --head --fail "http://localhost:8001/api/v1/server/ping"); do
    printf '.'
    sleep 1
done
echo "done!"

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
-e 'CONCORD_DOCKER_LOCAL_MODE=false' \
-e 'SERVER_API_BASE_URL=http://server:8001' \
docker.prod.walmart.com/walmartlabs/concord-agent:${VERSION}

docker run -d \
--name console \
--link server \
-p 8080:8080 \
-v /tmp/console/logs:/opt/concord/logs \
docker.prod.walmart.com/walmartlabs/concord-console:${VERSION}
