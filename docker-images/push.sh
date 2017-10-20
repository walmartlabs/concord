#!/bin/bash

TAG="$1"

docker tag walmartlabs/concord-base:${TAG} docker.prod.walmart.com/walmartlabs/concord-base:${TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-base:${TAG}

docker tag walmartlabs/concord-server:${TAG} docker.prod.walmart.com/walmartlabs/concord-server:${TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-server:${TAG}

docker tag walmartlabs/concord-console:${TAG} docker.prod.walmart.com/walmartlabs/concord-console:${TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-console:${TAG}

docker tag walmartlabs/concord-agent:${TAG} docker.prod.walmart.com/walmartlabs/concord-agent:${TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-agent:${TAG}

docker tag walmartlabs/concord-ansible:${TAG} docker.prod.walmart.com/walmartlabs/concord-ansible:${TAG}
docker push docker.prod.walmart.com/walmartlabs/concord-ansible:${TAG}
