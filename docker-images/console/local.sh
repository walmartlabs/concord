#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

docker rm -f console &> /dev/null

docker run -it --rm \
--network=host \
--name console \
-v "${BASE_DIR}/default.conf:/opt/concord/console/nginx/app.conf:ro" \
-v "${BASE_DIR}/cfg.js:/opt/concord/console/dist/cfg.js:ro" \
walmartlabs/concord-console
