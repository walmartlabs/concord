FROM walmartlabs/concord-base:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

# legacy ansible support
RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation openssh-clients sshpass util-linux python && yum clean all
RUN umask 0022
RUN pip install --upgrade -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple pip pbr ansible ara pywinrm kerberos requests_kerberos ujson grpcio grpcio-tools

RUN yum -y install docker-client

ENV DOCKER_HOST tcp://dind:2375

ADD docker-images/agent/target/dist/agent.tar.gz /opt/concord/agent/
COPY docker-images/agent/target/dist/runner.jar /opt/concord/agent/runner/runner.jar
COPY docker-images/agent/src/main/docker/start.sh /opt/concord/agent/

ENTRYPOINT /opt/concord/agent/start.sh


