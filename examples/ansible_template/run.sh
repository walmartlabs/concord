#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R playbook target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..

read -p "Username: " CURL_USER
curl -u ${CURL_USER} -H "Content-Type: application/octet-stream" -F archive=@target/payload.zip -F request=@request.json http://${SERVER_ADDR}/api/v1/process