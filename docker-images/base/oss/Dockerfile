FROM library/centos:8
LABEL maintainer="mtkunke@walmartlabs.com"

ARG jdk_version=1.8.0

ENTRYPOINT ["/usr/local/bin/concord_venv/bin/dumb-init", "--"]

# requires Git >= 2.3
RUN rpm --import /etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial && \
    dnf -y upgrade && \
    dnf -y install \
           dnf-plugins-core \
           java-${jdk_version}-openjdk-devel \
           which \
           libtool-ltdl \
           strace \
           python3 \
           python3-virtualenv \
           git \
           coreutils-single  \
           glibc-all-langpacks && \
    dnf clean all && \
    alternatives --set python /usr/bin/python3

ENV JAVA_HOME /usr/lib/jvm/java-${jdk_version}
ENV LC_CTYPE en_US.UTF-8
ENV LANG en_US.UTF-8

RUN virtualenv /usr/local/bin/concord_venv && \
    /usr/local/bin/concord_venv/bin/pip3 --no-cache-dir install dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord