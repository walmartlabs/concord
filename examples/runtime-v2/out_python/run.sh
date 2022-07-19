#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R concord.yml target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..

read -p "Username: " CURL_USER
curl -u ${CURL_USER} -F archive=@target/payload.zip http://${SERVER_ADDR}/api/v1/process
