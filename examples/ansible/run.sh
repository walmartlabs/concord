#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R concord.yml playbook target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..

curl -ikn -F archive=@target/payload.zip http://${SERVER_ADDR}/api/v1/process
