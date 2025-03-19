ARG docker_namespace=walmartlabs
ARG container_version=latest
ARG DEBIAN_FRONTEND=noninteractive

FROM ${docker_namespace}/concord-ansible:${container_version}
LABEL maintainer="amith.k.b@walmartlabs.com"

ENV DOCKER_HOST=tcp://dind:2375
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

USER root

RUN DEBIAN_FRONTEND=${DEBIAN_FRONTEND} apt-get -y install \
    ca-certificates \
    gnupg \
    lsb-release

RUN DEBIAN_FRONTEND=${DEBIAN_FRONTEND} mkdir -p /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    
RUN echo \
      "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \
      $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null && \
      apt-get update
      
RUN DEBIAN_FRONTEND=${DEBIAN_FRONTEND} apt-get -y install \
    uuid \
    docker-ce-cli \
    && apt-get clean

COPY --chown=concord:concord target/deps/ /home/concord/.m2/repository
ADD --chown=concord:concord target/dist/agent.tar.gz /opt/concord/agent

USER concord
CMD ["bash", "/opt/concord/agent/start.sh"]
