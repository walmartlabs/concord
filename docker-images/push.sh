#!/bin/bash

docker tag walmartlabs/concord-server:latest docker.prod.walmart.com/walmartlabs/concord-server:latest
docker push docker.prod.walmart.com/walmartlabs/concord-server:latest

docker tag walmartlabs/concord-console:latest docker.prod.walmart.com/walmartlabs/concord-console:latest
docker push docker.prod.walmart.com/walmartlabs/concord-console:latest

docker tag walmartlabs/concord-agent:latest docker.prod.walmart.com/walmartlabs/concord-agent:latest
docker push docker.prod.walmart.com/walmartlabs/concord-agent:latest

docker tag walmartlabs/concord-ansible:latest docker.prod.walmart.com/walmartlabs/concord-ansible:latest
docker push docker.prod.walmart.com/walmartlabs/concord-ansible:latest
