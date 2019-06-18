#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

export RUNNER_PATH="${BASE_DIR}/runner/runner.jar"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

if [[ -z "${CONCORD_CFG_FILE}" ]]; then
    CONCORD_CFG_FILE="${BASE_DIR}/default.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

exec java \
-Xmx256m \
-server \
-Dfile.encoding=UTF-8 \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-Dollie.conf=${CONCORD_CFG_FILE} \
-cp "${BASE_DIR}/lib/*" \
com.walmartlabs.concord.agent.Main
