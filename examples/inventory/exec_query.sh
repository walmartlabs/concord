#!/bin/bash

INVENTORY_NAME="myInventory"

echo "exec query"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @query_params.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/query/myQuery/exec"