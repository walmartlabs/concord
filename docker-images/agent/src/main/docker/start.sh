#!/bin/bash
APP_DIR="${concord.baseDir}/agent/concord-agent-${project.version}"

if [ ! -z "$SERVER_PORT_8101_TCP_ADDR" ]; then
    export SERVER_HOST="$SERVER_PORT_8101_TCP_ADDR"
    export SERVER_PORT="$SERVER_PORT_8101_TCP_PORT"
fi

export RUNNER_PATH="$APP_DIR/runner/concord-runner-${project.version}-jar-with-dependencies.jar"

python2 ${APP_DIR}/bin/launcher.py run
