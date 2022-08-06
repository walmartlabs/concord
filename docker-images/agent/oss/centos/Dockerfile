ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-ansible:$concord_version
LABEL maintainer="ibodrov@gmail.com"

ENV DOCKER_HOST tcp://dind:2375
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-bundle.crt

USER root

RUN dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo && \
    dnf -y install docker-ce-cli uuid && \
    dnf clean all

COPY --chown=concord:concord target/deps/ /home/concord/.m2/repository
ADD  --chown=concord:concord target/dist/agent.tar.gz /opt/concord/agent/

USER concord
CMD ["bash", "/opt/concord/agent/start.sh"]
