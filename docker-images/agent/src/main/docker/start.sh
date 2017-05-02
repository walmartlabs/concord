#!/bin/bash
APP_DIR="${concord.baseDir}/agent/concord-agent-${project.version}"

export RUNNER_PATH="$APP_DIR/runner/concord-runner-${project.version}-jar-with-dependencies.jar"

python2 ${APP_DIR}/bin/launcher.py run
