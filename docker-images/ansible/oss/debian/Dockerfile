ARG docker_namespace=walmartlabs
ARG container_version=latest
ARG DEBIAN_FRONTEND=noninteractive

FROM ${docker_namespace}/concord-base:${container_version}
LABEL maintainer="amith.k.b@walmartlabs.com"

ENV REQUESTS_CA_BUNDLE=/etc/ssl/certs/ca-certificates.crt

RUN mkdir -p /workspace
WORKDIR /workspace

COPY galaxy_requirements.yml galaxy_requirements.yml

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
    pip3 install --no-cache-dir --upgrade --break-system-packages --ignore-installed \
        "cryptography<=3.4.8" \
        "ansible-core>=2.14,<2.15" \
        "Appium-Python-Client<1.0" \
        "openshift==0.13.2" \
        "jinja2<=3.1.0" \
        boto3 \
        botocore \
        bzt \
        docker \
        kerberos \
        kubernetes \
        pyyaml \
        pbr \
        pyvmomi \
        "pywinrm>=0.4.3" \
        requests_kerberos \
        urllib3 \
        ujson \
        virtualenv

RUN umask 0022 && \
    ansible-galaxy collection install -p /usr/share/ansible/collections -r galaxy_requirements.yml

USER concord
