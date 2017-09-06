FROM docker.prod.walmart.com/walmartlabs/concord-base
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workplace

WORKDIR /workplace

ENV ANSIBLE_GATHERING smart
ENV ANSIBLE_HOST_KEY_CHECKING false
ENV ANSIBLE_RETRY_FILES_ENABLED false
ENV PYTHONPATH /ansible/lib

RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation  openssh-clients sshpass util-linux python && yum clean all && \
    umask 0022 && pip install --upgrade -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple pip pbr ansible ara pywinrm kerberos requests_kerberos ujson
