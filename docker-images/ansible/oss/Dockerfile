ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workspace
WORKDIR /workspace

RUN yum -y install \
    gcc \
    gcc-c++ \
    krb5-devel \
    krb5-libs \
    krb5-workstation \
    libffi-devel \
    openssh-clients \
    openssl-devel \
    python \
    python-devel \
    python-pip \
    rsync \
    sshpass \
    util-linux \
    && yum clean all

RUN umask 0022

RUN pip install --upgrade --ignore-installed \
    pip \
    setuptools

RUN pip install --upgrade --ignore-installed \
    "ansible>=2.6.10,<2.7.0" \
    "Appium-Python-Client<1.0" \
    boto3 \
    botocore \
    bzt \
    docker \
    kerberos \
    openshift \
    pbr \
    pyvmomi \
    pywinrm==0.4.1 \
    requests_kerberos \
    ujson \
    virtualenv

USER concord
