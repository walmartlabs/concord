#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

MAIN_CLASS="com.walmartlabs.concord.server.dist.Main"
if [[ "${CONCORD_COMMAND}" = "migrateDb" ]]; then
    MAIN_CLASS="com.walmartlabs.concord.server.MigrateDB"
fi

if [[ -z "${CONCORD_CFG_FILE}" ]]; then
    CONCORD_CFG_FILE="${BASE_DIR}/default.conf"
fi
echo "CONCORD_CFG_FILE: ${CONCORD_CFG_FILE}"

GC_LOG_DIR=${GC_LOG_DIR:-"${BASE_DIR}/logs/gc"}
mkdir -p ${GC_LOG_DIR}
echo "GC logs: ${GC_LOG_DIR}"

if [[ -z "${CONCORD_JAVA_OPTS}" ]]; then
    CONCORD_JAVA_OPTS="-Xms2g -Xmx2g"
fi
echo "CONCORD_JAVA_OPTS: ${CONCORD_JAVA_OPTS}"

if [[ -z "${CONCORD_TMP_DIR}" ]]; then
    export CONCORD_TMP_DIR="/tmp"
fi

echo "Using $(which java)"
java -version

JAVA_VERSION=$(java -version 2>&1 \
  | head -1 \
  | cut -d'"' -f2 \
  | sed 's/^1\.//' \
  | cut -d'.' -f1)

JDK_SPECIFIC_OPTS=""
if (( $JAVA_VERSION > 8 )); then
  echo "Applying JDK 9+ specific options..."
  JDK_SPECIFIC_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"
fi

if [[ -z "${EXTRA_CLASSPATH}" ]]; then
  EXTRA_CLASSPATH=""
fi

echo "EXTRA_CLASSPATH: ${EXTRA_CLASSPATH}"

exec java \
${CONCORD_JAVA_OPTS} \
${JDK_SPECIFIC_OPTS} \
-Dfile.encoding=UTF-8 \
-Djava.net.preferIPv4Stack=true \
-Djava.security.egd=file:/dev/./urandom \
-Dconcord.conf=${CONCORD_CFG_FILE} \
-cp "${BASE_DIR}/lib/*:${BASE_DIR}/ext/*:${BASE_DIR}/classes:${EXTRA_CLASSPATH}" \
"${MAIN_CLASS}"
