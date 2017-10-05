#!/bin/bash
APP_DIR="/opt/concord/agent"

if [ ! -z "$SERVER_PORT_8101_TCP_ADDR" ]; then
    export SERVER_HOST="$SERVER_PORT_8101_TCP_ADDR"
    export SERVER_PORT="$SERVER_PORT_8101_TCP_PORT"
fi

export RUNNER_PATH="$APP_DIR/runner/runner.jar"

# 16 * 60 * 60 * 1000ms
export MAX_PREFORK_AGE="57600000"

export DOCKER_SWEEPER_ENABLED="true"

exec java \
-Xmx256m \
-server \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.agent.Main
