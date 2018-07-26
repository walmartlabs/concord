#!/bin/bash
APP_DIR="/opt/concord/agent"

export RUNNER_PATH="$APP_DIR/runner/runner.jar"

exec java \
-Xmx256m \
-server \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/agent/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.agent.Main
