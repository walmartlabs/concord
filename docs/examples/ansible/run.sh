#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R playbook processes _main.json target/

echo "Collecting dependencies..."
mvn org.apache.maven.plugins:maven-dependency-plugin:2.10:copy-dependencies -DoutputDirectory=target/lib > /dev/null

cd target && zip -r payload.zip ./* > /dev/null && cd ..

echo "Sending the payload..."
curl -H "Content-Type: application/octet-stream" --data-binary @target/payload.zip http://$SERVER_ADDR/api/v1/process
