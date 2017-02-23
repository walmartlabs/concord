#!/bin/bash

# Helper script for IDEA

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [ "$1" == "server" ]; then
flow server "$DIR"
else
flow $@
fi

