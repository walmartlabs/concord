#!/bin/bash
APP_DIR="${concord.baseDir}/agent"

if [ ! -z "$SERVER_PORT_8101_TCP_ADDR" ]; then
    export SERVER_HOST="$SERVER_PORT_8101_TCP_ADDR"
    export SERVER_PORT="$SERVER_PORT_8101_TCP_PORT"
fi

export RUNNER_PATH="$APP_DIR/runner/runner.jar"

java \
-Xmx256m \
-server \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.agent.Main
