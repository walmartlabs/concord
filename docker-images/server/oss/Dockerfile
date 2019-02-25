ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 8001

ADD --chown=concord:concord target/dist/server.tar.gz /opt/concord/server/

USER concord
CMD ["bash", "/opt/concord/server/start.sh"]
