FROM walmartlabs/concord-base:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

RUN yum -y install nginx && yum clean all

EXPOSE 443
EXPOSE 8080

ADD console/target/concord-console-*-dist.tar.gz /opt/concord/console/dist/

COPY docker-images/console/src/main/docker/start.sh /opt/concord/console/
COPY docker-images/console/src/main/docker/nginx /opt/concord/console/nginx

ENTRYPOINT /opt/concord/console/start.sh


