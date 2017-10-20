#!/bin/bash

read -p "Username: " CURL_USER
read -p "Password:" -s CURL_PASSOWRD

INVENTORY_NAME="myInventory"
QUERY_NAME="endpointsByZypperVersion"
SERVER_ADDR="$1"

# create inventory
echo -e "\ncreating inventory ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: application/json" -d "{\"name\": \"$INVENTORY_NAME\"}" "http://${SERVER_ADDR}/api/v1/inventory"

# create inventory data
echo -e "\ncreating inventory data isp.s01160.ca_ansiblefacts ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: application/json" -d @isp.s01160.ca_ansiblefacts.json "http://${SERVER_ADDR}/api/v1/inventory/$INVENTORY_NAME/data/s01160/ansible_facts"
echo -e "\ncreating inventory data s05505.us_ansiblefacts ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: application/json" -d @isp.s05505.us_ansiblefacts.json "http://${SERVER_ADDR}/api/v1/inventory/$INVENTORY_NAME/data/s05505/ansible_facts"
echo -e "\ncreating inventory data s00524.us_ansiblefacts ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: application/json" -d @rxp.s00524.us_ansiblefacts.json "http://${SERVER_ADDR}/api/v1/inventory/$INVENTORY_NAME/data/s00524/ansible_facts"

# create query
echo -e "\ncreating query ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: text/plain" --data-binary @query.sql "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/query/${QUERY_NAME}"