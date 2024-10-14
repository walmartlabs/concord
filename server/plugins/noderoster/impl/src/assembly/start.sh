#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

MAIN_CLASS="com.walmartlabs.concord.noderoster.Main"

if [[ -z "${CONCORD_CFG_FILE}" ]]; then
    CONCORD_CFG_FILE="${BASE_DIR}/default.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

GC_LOG_DIR=${GC_LOG_DIR:-"${BASE_DIR}/logs/gc"}
mkdir -p ${GC_LOG_DIR}
echo "GC logs: ${GC_LOG_DIR}"

if [[ -z "${CONCORD_JAVA_OPTS}" ]]; then
    CONCORD_JAVA_OPTS="-Xms2g -Xmx2g -server"
fi
echo "CONCORD_JAVA_OPTS: ${CONCORD_JAVA_OPTS}"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

exec java \
${CONCORD_JAVA_OPTS} \
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
-Dconcord.conf=${CONCORD_CFG_FILE} \
-cp "${BASE_DIR}/lib/*" \
"${MAIN_CLASS}"
