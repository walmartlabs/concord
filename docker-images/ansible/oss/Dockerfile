ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
MAINTAINER "Yury Brigadirenko" <ybrigadirenko@walmartlabs.com>

RUN mkdir -p /workspace
WORKDIR /workspace

RUN yum -y install \
    gcc \
    krb5-devel \
    krb5-libs \
    krb5-workstation \
    libffi-devel \
    openssh-clients \
    openssl-devel \
    python \
    python-devel \
    python-pip \
    sshpass \
    util-linux \
    && yum clean all

RUN umask 0022 && pip install --upgrade \
    "ansible>=2.6.10,<2.7.0" \
    boto3 \
    botocore \
    docker \
    kerberos \
    pbr \
    pip \
    pyvmomi \
    pywinrm==0.3.0 \
    requests_kerberos \
    ujson 

USER concord
