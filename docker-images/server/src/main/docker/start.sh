#!/bin/bash
APP_DIR="${concord.baseDir}/server"

if [ "$DB" == "postgresql" ]; then
    export DB_DIALECT="POSTGRES_9_5"
    export DB_DRIVER="org.postgresql.Driver"
    export DB_URL="jdbc:postgresql://$PG_PORT_5432_TCP_ADDR:$PG_PORT_5432_TCP_PORT/postgres"
    export DB_USERNAME="postgres"
fi;

export DEPS_STORE_DIR="$APP_DIR";

exec java \
-Xmx1g \
-server \
-Djava.security.egd=file:/dev/./urandom \
-Dlogback.configurationFile=com/walmartlabs/concord/server/logback.xml \
-cp "${APP_DIR}/*" \
com.walmartlabs.concord.server.Main
