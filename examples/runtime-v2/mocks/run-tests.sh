#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R main.concord.yaml tests target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..

#read -p "Username: " CURL_USER
#curl -u ${CURL_USER}
curl -u myuser:q1 -F entryPoint=allTests -F concord.yaml=@tests-runner.concord.yaml -F archive=@target/payload.zip http://${SERVER_ADDR}/api/v1/process
