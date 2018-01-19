FROM library/centos:centos7
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0

RUN yum -y --enablerepo=extras install epel-release && \
    yum -y update && \
    yum -y install java-1.8.0-openjdk-devel which libtool-ltdl strace python-pip && \
    yum clean all

RUN pip install dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord
