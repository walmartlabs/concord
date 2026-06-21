#!/usr/bin/env bash

set -e

JDK_25_AMD64="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.3%2B9/OpenJDK25U-jdk_x64_linux_hotspot_25.0.3_9.tar.gz"
JDK_25_ARM64="https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.3%2B9/OpenJDK25U-jdk_aarch64_linux_hotspot_25.0.3_9.tar.gz"

case "${JDK_VERSION}-${TARGETARCH}" in
  "25-amd64")
    echo ${JDK_25_AMD64}
    ;;
  "25-arm64")
    echo ${JDK_25_ARM64}
    ;;
  *)
    >&2 echo "Unsupported JDK ${JDK_VERSION}-${TARGETARCH}"
    exit 1;
    ;;
esac
