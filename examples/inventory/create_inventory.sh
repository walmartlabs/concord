#!/bin/bash

INVENTORY_NAME="myInventory"

# create inventory
echo "creating inventory"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d "{\"name\": \"$INVENTORY_NAME\"}" "http://localhost:8001/api/v1/inventory"

# create inventory data
echo "creating inventory (host1) data-1"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_hw.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host1/hw"
echo "creating inventory (host1) data-2"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_network.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host1/net"
echo "creating inventory (host1) data-3"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_sw.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host1/sw"
echo "creating inventory (host1) data-4"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_app.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host1/sw/app"

echo "creating inventory (host2) data-1"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_hw.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host2/hw"
echo "creating inventory (host2) data-2"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d '{"ip": "128.10.10.11", "name": "xxx"}' "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host2/net"
echo "creating inventory (host2) data-3"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d @inventory_sw.json "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host2/sw"
echo "creating inventory (host2) data-4"
curl -f -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -H "Content-Type: application/json" -d '{"name": "my-app", "version": "1.1.0"}' "http://localhost:8001/api/v1/inventory/$INVENTORY_NAME/data/hosts/host2/sw/app"
