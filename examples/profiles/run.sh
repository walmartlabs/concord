#!/bin/bash

SERVER_ADDR="$1"

rm -rf target && mkdir target
cp -R concord.yml target/

cd target && zip -r payload.zip ./* > /dev/null && cd ..
curl  -H "Authorization: KVySNp3y6VNFlvTBghr1zg" -F archive=@target/payload.zip http://${SERVER_ADDR}/api/v1/process
