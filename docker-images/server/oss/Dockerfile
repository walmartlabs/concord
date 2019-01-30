ARG concord_version=latest

FROM walmartlabs/concord-base:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 8001

# requires Git >= 2.3
RUN yum -y install git && yum clean all

ADD target/dist/server.tar.gz /opt/concord/server/

RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/server/start.sh"]
