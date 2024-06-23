#!/usr/bin/env bash
# run a local version of node installed by Maven
export SET NODE_OPTIONS=--openssl-legacy-provider
./target/node/node ./target/node/node_modules/npm/bin/npm-cli.js "$@"
