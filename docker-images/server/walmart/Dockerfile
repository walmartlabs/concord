ARG concord_version=latest

FROM docker.prod.walmart.com/walmartlabs/concord-base:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 8001
EXPOSE 8101
EXPOSE 5555

# moved to the base image
# RUN yum -y install git && yum clean all

ADD docker-images/server/target/dist/server.tar.gz /opt/concord/server/
COPY docker-images/server/src/main/docker/start.sh /opt/concord/server/

COPY docker-images/server/src/main/docker/jmx/* /opt/concord/server/jmx/
RUN chmod 400 /opt/concord/server/jmx/*

RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/server/start.sh"]
