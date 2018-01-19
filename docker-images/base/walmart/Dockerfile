FROM hub.docker.prod.walmart.com/library/centos:centos7
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

RUN sed -i -e 's/enabled=1/enabled=0/' /etc/yum/pluginconf.d/fastestmirror.conf
COPY docker-images/base/src/main/docker/base.repo /etc/yum.repos.d/

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0

RUN yum-config-manager --disable base extras updates && \
    yum -y update && \
    yum -y install java-1.8.0-openjdk-devel which libtool-ltdl strace python-pip && \
    yum clean all

RUN pip install -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord
