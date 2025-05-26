ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
LABEL maintainer="ibodrov@gmail.com"

EXPOSE 8001

ADD --chown=concord:concord target/dist/server.tar.gz /opt/concord/server/
ADD --chown=concord:concord target/dist/console.tar.gz /opt/concord/console/

RUN mkdir -p /opt/concord/server/logs && \
    chown -R concord:concord /opt/concord/server/logs

USER concord

ENV BASE_RESOURCE_PATH=/opt/concord/console/
CMD ["bash", "/opt/concord/server/start.sh"]
