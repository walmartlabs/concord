#!/bin/bash
APP_DIR="/opt/concord/server/"

export DEPS_STORE_DIR="$APP_DIR";

exec java \
-Xmx1g \
-server \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/server/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.server.Main
