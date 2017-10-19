#!/bin/bash

INVENTORY_NAME="myInventory"

echo "creating query"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: text/plain" --data-binary @query.sql "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/query/myQuery"