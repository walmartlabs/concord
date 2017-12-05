#!/bin/bash

SRC_TAG="$1"
DST_TAG="$2"

docker tag walmartlabs/concord-base:${SRC_TAG} docker.prod.walmart.com/walmartlabs/concord-base:${DST_TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-base:${DST_TAG}

docker tag walmartlabs/concord-server:${SRC_TAG} docker.prod.walmart.com/walmartlabs/concord-server:${DST_TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-server:${DST_TAG}

docker tag walmartlabs/concord-console:${SRC_TAG} docker.prod.walmart.com/walmartlabs/concord-console:${DST_TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-console:${DST_TAG}

docker tag walmartlabs/concord-agent:${SRC_TAG} docker.prod.walmart.com/walmartlabs/concord-agent:${DST_TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-agent:${DST_TAG}

docker tag walmartlabs/concord-ansible:${SRC_TAG} docker.prod.walmart.com/walmartlabs/concord-ansible:${DST_TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-ansible:${DST_TAG}
