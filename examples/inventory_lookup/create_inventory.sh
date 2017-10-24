#!/bin/bash

read -p "Username: " CURL_USER
read -p "Password:" -s CURL_PASSOWRD

INVENTORY_NAME="myInventory2"
QUERY_NAME="lookupQuery"
SERVER_ADDR="$1"

# create inventory
echo -e "\nCreating an inventory ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: application/json" -d "{\"name\": \"$INVENTORY_NAME\"}" "http://${SERVER_ADDR}/api/v1/inventory"

# create query
echo -e "\nCreating a query ..."
curl -f -u ${CURL_USER}:${CURL_PASSOWRD} -H "Content-Type: text/plain" --data-binary @query.sql "http://${SERVER_ADDR}/api/v1/inventory/$INVENTORY_NAME/query/${QUERY_NAME}"