FROM hub.docker.prod.walmart.com/library/centos:centos7
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

RUN sed -i -e 's/enabled=1/enabled=0/' /etc/yum/pluginconf.d/fastestmirror.conf
COPY base.repo /etc/yum.repos.d/
RUN yum-config-manager --disable base extras updates

RUN yum -y update
RUN yum -y install java-1.8.0-openjdk-devel which libtool-ltdl
RUN yum clean all

ENV JAVA_HOME /usr/lib/jvm/java-1.8.0