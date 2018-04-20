ARG concord_version=latest

FROM walmartlabs/concord-base:$concord_version
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workspace
WORKDIR /workspace

RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation openssh-clients sshpass util-linux python && yum clean all

RUN umask 0022
RUN pip install --upgrade pip pbr ansible==2.4.3 pywinrm==0.3.0 kerberos requests_kerberos ujson grpcio grpcio-tools docker-py

USER concord