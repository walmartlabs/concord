#!/bin/bash

MAIN_CLASS="com.walmartlabs.concord.server.Main"

if [ "${CONCORD_COMMAND}" = "migrateDb" ]; then
    MAIN_CLASS="com.walmartlabs.concord.server.MigrateDB"
fi

APP_DIR="/opt/concord/server"

CFG_FILE="";
if [ ! -z "$CONCORD_CFG_FILE" ]; then
    CFG_FILE="-Dollie.conf=$CONCORD_CFG_FILE"
fi

GC_LOG_DIR=${GC_LOG_DIR:-"${APP_DIR}/logs/gc"}
mkdir -p ${GC_LOG_DIR}

echo "GC logs: ${GC_LOG_DIR}"

exec java \
-Xms2g \
-Xmx2g \
-XX:+PrintGC \
-XX:+PrintGCTimeStamps \
-XX:+PrintGCDateStamps \
-XX:+PrintGCDetails \
-XX:+PrintGCApplicationStoppedTime \
-XX:+PrintGCApplicationConcurrentTime \
-Xloggc:${GC_LOG_DIR}/gc.log \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=10 \
-XX:GCLogFileSize=10M \
-server \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
$CFG_FILE \
-cp "${APP_DIR}/*" \
"${MAIN_CLASS}"
