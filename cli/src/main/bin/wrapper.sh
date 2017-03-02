#!/bin/bash
set -euo pipefail

WORKDIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
BIN_DIR="$WORKDIR/target/bin"
SCRIPT_PATH="$BIN_DIR/concord.sh"

SCRIPT_URL="http://gec-nexus.prod.glb.prod.walmart.com/nexus/content/repositories/devtools-snapshots/com/walmartlabs/concord/concord-cli/0.0.1-SNAPSHOT/concord-cli-0.0.1-20170302.023914-2.sh"
SCRIPT_SHA256="3e6e5734dc28e747941d68c0cb9ce56eb9aae8c899a671dc919d19e3adb9def6"

function wrongSha() {
    local _sum=`shasum -b -a 256 "$SCRIPT_PATH" | cut -d ' ' -f 1`
    [ "$SCRIPT_SHA256" != "$_sum" ]
}

if [ ! -f "$SCRIPT_PATH" ] || wrongSha; then
    echo "Missing or invalid script, downloading..."

    mkdir -p "$BIN_DIR"
    curl "$SCRIPT_URL" > "$SCRIPT_PATH"

    if wrongSha; then
        echo "Invalid checksum"
        exit 1
    fi

    chmod +x "$SCRIPT_PATH"
fi;

${SCRIPT_PATH} "$@"