FROM library/debian:stable
LABEL maintainer="amith.k.b@walmartlabs.com"

ENTRYPOINT ["/usr/local/bin/concord_venv/bin/dumb-init", "--"]

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install openssh-client libltdl-dev wget unzip diffutils strace git gdebi-core && \
    apt-get -y install python3 python3-pip coreutils locales locales-all curl && \
    apt-get clean && \
    ln -f -s /usr/bin/python3 /usr/bin/python && \
    pip3 install --no-cache-dir dumb-init virtualenv
    

ARG jdk_url
RUN curl --location --output /tmp/jdk.tar.gz ${jdk_url} && \
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
