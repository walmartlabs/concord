#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export RUNNER_V1_PATH="${BASE_DIR}/runner/runner-v1.jar"
export RUNNER_V2_PATH="${BASE_DIR}/runner/runner-v2.jar"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

if [[ -z "${CONCORD_JAVA_OPTS}" ]]; then
    CONCORD_JAVA_OPTS="-Xmx256m"
fi
echo "CONCORD_JAVA_OPTS: ${CONCORD_JAVA_OPTS}"

if [[ -z "${CONCORD_CFG_FILE}" ]]; then
    CONCORD_CFG_FILE="${BASE_DIR}/default.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

echo "Using $(which java)"
java -version

exec java \
${CONCORD_JAVA_OPTS} \
-Dfile.encoding=UTF-8 \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-Dollie.conf=${CONCORD_CFG_FILE} \
-cp "${BASE_DIR}/lib/*" \
com.walmartlabs.concord.agent.Main
