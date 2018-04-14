#!/bin/bash

APP_DIR="/opt/concord/server"

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
-Dlogback.configurationFile=com/walmartlabs/concord/server/logback.xml \
-Dcom.sun.management.jmxremote \
-Dcom.sun.management.jmxremote.port=5555 \
-Dcom.sun.management.jmxremote.ssl=false \
-Dcom.sun.management.jmxremote.authenticate=true \
-Dcom.sun.management.jmxremote.access.file=${APP_DIR}/jmx/jmx.access \
-Dcom.sun.management.jmxremote.password.file=${APP_DIR}/jmx/jmx.password \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.server.Main
