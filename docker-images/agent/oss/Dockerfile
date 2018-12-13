ARG concord_version=latest

FROM walmartlabs/concord-ansible:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENV DOCKER_HOST tcp://dind:2375
ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-bundle.crt

USER root

RUN yum -y install docker-client git

COPY target/deps/ /home/concord/.m2/repository
RUN chown -R concord:concord /home/concord/.m2

ADD target/dist/agent.tar.gz /opt/concord/agent/
COPY target/dist/runner.jar /opt/concord/agent/runner/runner.jar
COPY src/main/docker/start.sh /opt/concord/agent/
RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/agent/start.sh"]
