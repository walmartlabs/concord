#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R playbook target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..

echo "Sending the payload..."
curl -H "Authorization: auBy4eDWrKWsyhiDp3AQiw" -F archive=@target/payload.zip -F request=@request.json http://${SERVER_ADDR}/api/v1/process
