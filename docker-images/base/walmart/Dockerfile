FROM hub.docker.prod.walmart.com/library/centos:centos7
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

RUN sed -i -e 's/enabled=1/enabled=0/' /etc/yum/pluginconf.d/fastestmirror.conf
COPY docker-images/base/src/main/docker/base.repo /etc/yum.repos.d/

ENTRYPOINT ["/usr/bin/dumb-init", "--"]
ENV JAVA_HOME /usr/lib/jvm/java-1.8.0

RUN yum-config-manager --disable base extras updates && \
    yum -y update && \
    yum -y install java-1.8.0-openjdk-devel which libtool-ltdl strace python-pip openssl ca-certificates && \
    yum clean all

RUN pip install -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord

# Walmart Root CA certs

RUN curl --insecure -o /tmp/rootca.crt https://pki.wal-mart.com/pki/WalmartRootCA.crt && \
    curl --insecure -o /tmp/rootca256.crt https://pki.wal-mart.com/pki/WalmartRootCA-SHA256.crt && \
    openssl x509 -inform DER -in /tmp/rootca.crt -out /tmp/rootca.pem -outform PEM && \
    openssl x509 -inform DER -in /tmp/rootca256.crt -out /tmp/rootca256.pem -outform PEM && \
    cp /tmp/rootca.pem /etc/pki/ca-trust/source/anchors/WalmartRootCA.pem && \
    cp /tmp/rootca256.pem /etc/pki/ca-trust/source/anchors/WalmartRootCA256.pem && \
    update-ca-trust force-enable && update-ca-trust extract
