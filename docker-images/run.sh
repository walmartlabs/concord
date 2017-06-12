#!/bin/bash

docker rm -f agent server console

docker run -d \
-p 8001:8001 \
--name server \
-v /opt/concord/conf/ldap.properties:/opt/concord/conf/ldap.properties:ro \
-e 'LDAP_CFG=/opt/concord/conf/ldap.properties' \
walmartlabs/concord-server

docker run -d \
--name agent \
--link server \
walmartlabs/concord-agent

docker run -d \
-p 8080:8080 \
--name console \
--link server \
walmartlabs/concord-console
