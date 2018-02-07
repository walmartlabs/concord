ARG concord_version=latest

FROM docker.prod.walmart.com/walmartlabs/concord-base:$concord_version
MAINTAINER "Ivan Bodrov" <ibodrov@walmartlabs.com>

ENV DOCKER_HOST tcp://dind:2375

# legacy ansible support
RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation openssh-clients sshpass util-linux python && yum clean all
RUN umask 0022
RUN pip install --upgrade -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple pip pbr ansible==2.4.3 pywinrm==0.3.0 kerberos requests_kerberos ujson grpcio grpcio-tools docker-py

RUN yum -y install docker-client

COPY docker-images/agent/target/deps/ /home/concord/.m2/repository
RUN chown -R concord:concord /home/concord/.m2

ADD docker-images/agent/target/dist/agent.tar.gz /opt/concord/agent/
COPY docker-images/agent/target/dist/runner.jar /opt/concord/agent/runner/runner.jar
COPY docker-images/agent/src/main/docker/start.sh /opt/concord/agent/
RUN chown -R concord:concord /opt/concord

USER concord
CMD ["bash", "/opt/concord/agent/start.sh"]


