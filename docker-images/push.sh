#!/bin/bash

TAG="$1"

docker push walmartlabs/concord-server:${TAG}
docker push walmartlabs/concord-ansible:${TAG}
docker push walmartlabs/concord-agent:${TAG}
docker push walmartlabs/concord-agent:${TAG}
