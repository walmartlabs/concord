FROM walmartlabs/concord-base:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 8001
EXPOSE 8101

ADD server/dist/target/concord-server-dist-*-SNAPSHOT.tar.gz /opt/concord/server/
COPY docker-images/server/src/main/docker/start.sh /opt/concord/server/

ENTRYPOINT /opt/concord/server/start.sh


