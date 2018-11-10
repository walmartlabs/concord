#!/bin/bash
APP_DIR="/opt/concord/agent"

export RUNNER_PATH="$APP_DIR/runner/runner.jar"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

exec java \
-Xmx256m \
-server \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.agent.Main
