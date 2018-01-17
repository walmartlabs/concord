FROM walmartlabs/concord-base
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workspace
WORKDIR /workspace

ENV ANSIBLE_GATHERING smart
ENV ANSIBLE_HOST_KEY_CHECKING false
ENV ANSIBLE_RETRY_FILES_ENABLED false

RUN yum -y install gcc python-devel python-pip libffi-devel openssl-devel krb5-devel krb5-libs krb5-workstation openssh-clients sshpass util-linux python && yum clean all

RUN umask 0022
RUN pip install --upgrade -i https://nexus.prod.walmart.com/nexus/content/repositories/pypi-proxy/simple pip pbr ansible==2.4.2 pywinrm kerberos requests_kerberos ujson grpcio grpcio-tools
