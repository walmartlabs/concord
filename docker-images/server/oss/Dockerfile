ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
LABEL maintainer="ibodrov@gmail.com"

EXPOSE 8001

ADD --chown=concord:concord target/dist/server.tar.gz /opt/concord/server/

RUN mkdir -p /opt/concord/server/logs && \
    chown -R concord:concord /opt/concord/server/logs

USER concord

CMD ["bash", "/opt/concord/server/start.sh"]
