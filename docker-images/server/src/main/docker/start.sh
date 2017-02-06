#!/bin/bash
APP_DIR="${concord.baseDir}/server/concord-server-${project.version}"

export RUNNER_PATH="$APP_DIR/runner/concord-runner-${project.version}-jar-with-dependencies.jar"

if [ ! -z "$AGENT_PORT_8002_TCP_ADDR" ]; then
    export AGENT_URL="http://$AGENT_PORT_8002_TCP_ADDR:$AGENT_PORT_8002_TCP_PORT"
fi

if [ "$DB" == "postgresql" ]; then
    export DB_DIALECT="POSTGRES_9_5"
    export DB_DRIVER="org.postgresql.Driver"
    export DB_URL="jdbc:postgresql://$PG_PORT_5432_TCP_ADDR:$PG_PORT_5432_TCP_PORT/postgres"
    export DB_USERNAME="postgres"
fi;

python2 $APP_DIR/bin/launcher.py run
