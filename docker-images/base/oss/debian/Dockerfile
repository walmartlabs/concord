ARG DEBIAN_FRONTEND=noninteractive

FROM library/debian:stable
LABEL maintainer="amith.k.b@walmartlabs.com"

ENTRYPOINT ["/usr/local/bin/concord_venv/bin/dumb-init", "--"]

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install openssh-client libltdl-dev wget unzip diffutils strace git gdebi-core \
               python3 python3-pip python-is-python3 coreutils locales locales-all curl bash && \
    apt-get clean && \
    pip3 install --no-cache-dir --break-system-packages dumb-init virtualenv
    

ADD --chmod=755 ./get_jdk_url.sh /tmp/
ADD --chmod=755 ./get_arch.sh /tmp/
ARG jdk_version
ENV JDK_VERSION=${jdk_version}
RUN export DEFAULT_TARGETARCH=$(/tmp/get_arch.sh); \
    export TARGETARCH=${TARGETARCH:-${DEFAULT_TARGETARCH}}; \
    curl --location --output /tmp/jdk.tar.gz $(/tmp/get_jdk_url.sh) && \
    mkdir /opt/jdk && \
    tar xpf /tmp/jdk.tar.gz --strip 1 -C /opt/jdk && \
    rm /tmp/jdk.tar.gz

ENV JAVA_HOME=/opt/jdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV LC_CTYPE=en_US.UTF-8
ENV LANG=en_US.UTF-8

RUN virtualenv /usr/local/bin/concord_venv && \
    /usr/local/bin/concord_venv/bin/pip3 --no-cache-dir install dumb-init

RUN groupadd -g 456 concord && \
    useradd --no-log-init -u 456 -g concord -m -s /sbin/nologin concord && \
    echo "[safe]\n\tdirectory = *\n" > ~concord/.gitconfig && \
    chown concord:concord ~concord/.gitconfig

# Point /bin/sh to bash from dash
RUN echo "dash dash/sh boolean false" | debconf-set-selections && \
    DEBIAN_FRONTEND=${DEBIAN_FRONTEND} dpkg-reconfigure dash
