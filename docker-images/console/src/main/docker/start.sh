#!/bin/bash

if [ ! -z "$SERVER_PORT_8001_TCP_ADDR" ]; then
    export SERVER_ADDR="$SERVER_PORT_8001_TCP_ADDR"
    export SERVER_PORT="$SERVER_PORT_8001_TCP_PORT"
fi

NGINX_CONF_TEMPLATE="app.template"
if [ "${SSL}" == "true" ]; then
    NGINX_CONF_TEMPLATE="app.ssl.template"
fi
if [ "${TERMINATED_SSL}" == "true" ]; then
    NGINX_CONF_TEMPLATE="app.terminated_ssl.template"
fi

NGINX_CONF_DIR="/opt/concord/console/nginx/conf"

cp ${NGINX_CONF_DIR}/${NGINX_CONF_TEMPLATE} ${NGINX_CONF_DIR}/app.conf

/usr/bin/sed -i "s,SERVER_ADDR,$SERVER_ADDR,g;s,SERVER_PORT,$SERVER_PORT,g" ${NGINX_CONF_DIR}/app.conf
/usr/bin/sed -i "s,SSL_CERT_PATH,$SSL_CERT_PATH,g" ${NGINX_CONF_DIR}/app.conf

mkdir -p /opt/concord/console/logs

exec /usr/sbin/nginx -c ${NGINX_CONF_DIR}/nginx.conf
