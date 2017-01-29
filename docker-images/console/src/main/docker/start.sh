#!/bin/bash

SERVER_ADDR="$SERVER_PORT_8001_TCP_ADDR"
SERVER_PORT="$SERVER_PORT_8001_TCP_PORT"

NGINX_CONF_DIR="/opt/concord/console/nginx/conf"

/usr/bin/sed "s/SERVER_ADDR/$SERVER_ADDR/g;s/SERVER_PORT/$SERVER_PORT/g" $NGINX_CONF_DIR/app.template > $NGINX_CONF_DIR/app.conf
/usr/sbin/nginx -c $NGINX_CONF_DIR/nginx.conf
