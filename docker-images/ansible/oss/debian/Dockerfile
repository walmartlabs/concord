ARG docker_namespace=walmartlabs
ARG container_version=latest
ARG DEBIAN_FRONTEND=noninteractive

FROM ${docker_namespace}/concord-base:${container_version}
LABEL maintainer="amith.k.b@walmartlabs.com"

ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

RUN mkdir -p /workspace
WORKDIR /workspace

RUN DEBIAN_FRONTEND=${DEBIAN_FRONTEND} apt-get -y install \
    gcc \
    g++ \
    libkrb5-dev \
    krb5-user \
    libpam-krb5 \
    libffi-dev \
    openssh-client \
    libssl-dev \
    python3.8-dev \
    rsync \
    util-linux \
    && apt-get clean 

ENV PATH=/usr/local/bin/concord_venv/bin:${PATH}
ENV VIRTUAL_ENV=/usr/local/bin/concord_venv

RUN umask 0022 && \
    pip3 install --no-cache-dir --upgrade --ignore-installed \
    "cryptography<=3.3.1" \
    "ansible>=2.8.0,<2.9.0" \
    "Appium-Python-Client<1.0" \
    "openshift<=0.11.2" \
    "jinja2<=3.0.3" \
    boto3 \
    botocore \
    bzt \
    docker \
    kerberos \
    pbr \
    pyvmomi \
    "pywinrm>=0.4.1" \
    requests_kerberos \
    urllib3 \
    ujson 

USER concord
