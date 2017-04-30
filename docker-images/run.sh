#!/bin/bash

docker rm -f agent server console

docker run -d -p 8002:8002 \
--name agent \
walmartlabs/concord-agent

docker run -d -p 8001:8001 \
--name server \
-v /opt/concord:/opt/concord/data:ro \
-v /opt/concord/tmp:/tmp \
-e 'LDAP_CFG=/opt/concord/data/conf/ldap.properties' \
--link agent \
walmartlabs/concord-server

docker run -d -p 8080:8080 \
--name console \
--link server \
-v /opt/concord/console/landing:/opt/concord/console/landing:ro \
walmartlabs/concord-console
