#!/bin/bash

read -p "Username: " CURL_USER

INVENTORY_NAME="myInventory"

echo "creating query"
curl -f -u ${CURL_USER} -H "Content-Type: text/plain" --data-binary @query.sql "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/query/myQuery"