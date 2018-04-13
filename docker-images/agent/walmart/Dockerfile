FROM docker.prod.walmart.com/walmartlabs/concord-ansible:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENV DOCKER_HOST tcp://dind:2375

USER root

# moved to the base image
# RUN yum -y install docker-client

COPY docker-images/agent/target/deps/ /home/concord/.m2/repository
RUN chown -R concord:concord /home/concord/.m2

ADD docker-images/agent/target/dist/agent.tar.gz /opt/concord/agent/
COPY docker-images/agent/target/dist/runner.jar /opt/concord/agent/runner/runner.jar
COPY docker-images/agent/src/main/docker/start.sh /opt/concord/agent/
RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/agent/start.sh"]
