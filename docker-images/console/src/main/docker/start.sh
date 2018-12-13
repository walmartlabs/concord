#!/bin/bash

BASE_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
exec /usr/sbin/nginx -c ${BASE_DIR}/nginx/nginx.conf
