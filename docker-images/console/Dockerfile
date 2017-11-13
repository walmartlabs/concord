FROM walmartlabs/concord-base:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

EXPOSE 80
EXPOSE 443
EXPOSE 8080

RUN yum -y install nginx && yum clean all

ADD docker-images/console/target/dist/console.tar.gz /opt/concord/console/dist/
COPY docker-images/console/src/main/docker/start.sh /opt/concord/console/
COPY docker-images/console/src/main/docker/nginx /opt/concord/console/nginx

CMD ["bash", "/opt/concord/console/start.sh"]

