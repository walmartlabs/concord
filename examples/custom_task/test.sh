#!/usr/bin/env bash

set -e

SERVER_ADDR="$1"
CONCORD_YAML="$2"

# build the task
mvn clean package -DskipTests

# prepare the target dir
rm -rf target/test && mkdir -p target/test

# copy the test flow
cp ${CONCORD_YAML:-test.yml} target/test/concord.yml

# copy the task's JAR...
mkdir -p target/test/lib/
cp target/custom-task-*-SNAPSHOT.jar target/test/lib/

# ...and dependencies
mvn dependency:copy-dependencies -DoutputDirectory=/tmp/deps -Dmdep.useSubDirectoryPerScope
cp /tmp/deps/compile/*.jar target/test/lib/

# create the payload archive
pushd target/test && zip -r payload.zip ./* > /dev/null && popd

read -p "Username: " CURL_USER
curl -u ${CURL_USER} -F archive=@target/test/payload.zip http://${SERVER_ADDR}/api/v1/process
