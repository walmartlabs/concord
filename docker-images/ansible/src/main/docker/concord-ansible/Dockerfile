FROM walmartlabs/concord-base
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workplace

WORKDIR /workplace

ENV ANSIBLE_GATHERING smart
ENV ANSIBLE_HOST_KEY_CHECKING false
ENV ANSIBLE_RETRY_FILES_ENABLED false
ENV PYTHONPATH /ansible/lib

RUN yum -y install ansible openssh-clients sshpass util-linux python && yum clean all