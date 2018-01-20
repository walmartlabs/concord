ARG concord_version=latest

FROM walmartlabs/concord-base:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 8001
EXPOSE 8101

RUN yum -y install git && yum clean all

ADD docker-images/server/target/dist/server.tar.gz /opt/concord/server/
COPY docker-images/server/src/main/docker/start.sh /opt/concord/server/

RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/server/start.sh"]
