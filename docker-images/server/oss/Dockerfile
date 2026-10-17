ARG docker_namespace=walmartlabs
ARG concord_version=latest
ARG concord_base_image=$docker_namespace/concord-base:$concord_version

FROM $concord_base_image
LABEL maintainer="ibodrov@gmail.com"

EXPOSE 8001

COPY --chown=concord:concord target/dist/ /opt/concord/server/
COPY --chown=concord:concord --chmod=755 target/dist/start.sh /opt/concord/server/start.sh

RUN mkdir -p /opt/concord/server/logs && \
    chown -R concord:concord /opt/concord/server/logs

USER concord

CMD ["bash", "/opt/concord/server/start.sh"]
