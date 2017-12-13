FROM walmartlabs/concord-base:latest
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENV DOCKER_HOST tcp://dind:2375

# legacy ansible support
RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation openssh-clients sshpass util-linux python && yum clean all
RUN umask 0022
RUN pip install --upgrade -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple pip pbr ansible==2.3.2 pywinrm kerberos requests_kerberos ujson grpcio grpcio-tools

RUN yum -y install docker-client

COPY docker-images/agent/target/deps/ /root/.m2/repository/

ADD docker-images/agent/target/dist/agent.tar.gz /opt/concord/agent/
COPY docker-images/agent/target/dist/runner.jar /opt/concord/agent/runner/runner.jar
COPY docker-images/agent/src/main/docker/start.sh /opt/concord/agent/

CMD ["bash", "/opt/concord/agent/start.sh"]


