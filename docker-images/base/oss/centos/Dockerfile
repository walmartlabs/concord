FROM library/centos:8
LABEL maintainer="mtkunke@walmartlabs.com"

ENTRYPOINT ["/usr/local/bin/concord_venv/bin/dumb-init", "--"]

RUN echo 'fastestmirror=true' >> /etc/dnf/dnf.conf && \
    echo 'max_parallel_downloads=10' >> /etc/dnf/dnf.conf

# update mirrors to point to vault.centos.org(older versions)
RUN cd /etc/yum.repos.d/ && \
    sed -i 's/mirrorlist/#mirrorlist/g' /etc/yum.repos.d/CentOS-* && \
    sed -i 's|#baseurl=http://mirror.centos.org|baseurl=http://vault.centos.org|g' /etc/yum.repos.d/CentOS-*
    
# requires Git >= 2.3
RUN rpm --import /etc/pki/rpm-gpg/RPM-GPG-KEY-centosofficial && \
    dnf -y upgrade && \
    dnf -y install \
           dnf-plugins-core \
           which \
           libtool-ltdl \
           strace \
           python39 \
           python39-devel \
           git \
           coreutils-single  \
           glibc-all-langpacks && \
    dnf clean all && \
    alternatives --set python /usr/bin/python3.9 && \
    alternatives --set python3 /usr/bin/python3.9 && \
    alternatives --install /usr/bin/pip3 pip3 /usr/bin/pip3.9 0 && \
    alternatives --install /usr/bin/pip pip /usr/bin/pip3.9 0

RUN umask 0022 && \
    pip3 install --no-cache-dir --upgrade --ignore-installed "virtualenv"

ADD --chmod=755 ./get_jdk_url.sh /tmp/
ARG jdk_version
ENV JDK_VERSION=${jdk_version}
ENV TARGETARCH=${TARGETARCH:-"amd64"}
RUN curl --location --output /tmp/jdk.tar.gz $(/tmp/get_jdk_url.sh) && \
    mkdir /opt/jdk && \
    tar xpf /tmp/jdk.tar.gz --strip 1 -C /opt/jdk && \
    rm /tmp/jdk.tar.gz

ENV JAVA_HOME /opt/jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV LC_CTYPE en_US.UTF-8
ENV LANG en_US.UTF-8

RUN virtualenv /usr/local/bin/concord_venv && \
    /usr/local/bin/concord_venv/bin/pip3 --no-cache-dir install dumb-init

RUN groupadd -g 456 concord && useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord
