#!/bin/bash

read -p "Username: " CURL_USER

INVENTORY_NAME="myInventory"

echo "exec query"
curl -f -u ${CURL_USER} -H "Content-Type: application/json" -d @query_params.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/query/myQuery/exec"