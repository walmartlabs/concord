ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
LABEL maintainer="ybrigadirenko@walmartlabs.com"

RUN mkdir -p /workspace
WORKDIR /workspace

RUN dnf -y install \
           gcc \
           gcc-c++ \
           krb5-devel \
           krb5-libs \
           krb5-workstation \
           libffi-devel \
           openssh-clients \
           openssl-devel \
           rsync \
           util-linux && \
    dnf clean all


ENV PATH=/usr/local/bin/concord_venv/bin:${PATH}
ENV VIRTUAL_ENV=/usr/local/bin/concord_venv

RUN umask 0022 && \
    pip3 install --no-cache-dir --upgrade --ignore-installed \
        "cryptography<=3.3.1"
        "ansible>=2.8.0,<2.9.0" \
        "Appium-Python-Client<1.0" \
        boto3 \
        botocore \
        bzt \
        docker \
        kerberos \
        openshift \
        pbr \
        pyvmomi \
        pywinrm>=0.4.1 \
        requests_kerberos \
        urllib3 \
        ujson 

USER concord
