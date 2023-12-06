ARG docker_namespace=walmartlabs
ARG concord_version=latest

FROM $docker_namespace/concord-base:$concord_version
LABEL maintainer="ybrigadirenko@walmartlabs.com"

RUN mkdir -p /workspace
WORKDIR /workspace

COPY galaxy_requirements.yml galaxy_requirements.yml

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
