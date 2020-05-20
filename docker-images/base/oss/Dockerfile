FROM library/centos:8
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0

# requires Git >= 2.3
RUN yum -y --enablerepo=extras install epel-release && \
    yum -y update && \
    yum -y install yum-utils java-1.8.0-openjdk-devel which libtool-ltdl strace python2 python2-pip git coreutils-single && \
    yum clean all

RUN alternatives --set python /usr/bin/python2
RUN alternatives --install /usr/bin/pip pip /usr/bin/pip2 0

RUN pip install --upgrade --ignore-installed pip setuptools && pip install dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord
