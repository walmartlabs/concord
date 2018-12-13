#!/bin/bash
APP_DIR="/opt/concord/agent"

export RUNNER_PATH="$APP_DIR/runner/runner.jar"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

CFG_FILE="";
if [[ ! -z "${CONCORD_CFG_FILE}" ]]; then
    CFG_FILE="-Dollie.conf=${CONCORD_CFG_FILE}"
fi

exec java \
-Xmx256m \
-server \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
${CFG_FILE} \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.agent.Main
