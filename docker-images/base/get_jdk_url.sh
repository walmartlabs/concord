#!/usr/bin/env bash

set -e

JDK_17_AMD64="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_x64_linux_hotspot_17.0.15_6.tar.gz"
JDK_17_ARM64="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.15%2B6/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.15_6.tar.gz"
JDK_21_AMD64="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_x64_linux_hotspot_21.0.7_6.tar.gz"
JDK_21_ARM64="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.7%2B6/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.7_6.tar.gz"

case "${JDK_VERSION}-${TARGETARCH}" in
  "17-amd64")
    echo ${JDK_17_AMD64}
    ;;
  "17-arm64")
    echo ${JDK_17_ARM64}
    ;;
  "21-amd64")
    echo ${JDK_21_AMD64}
    ;;
  "21-arm64")
    echo ${JDK_21_ARM64}
    ;;
  *)
    >&2 echo "Unsupported JDK ${JDK_VERSION}-${TARGETARCH}"
    exit 1;
    ;;
esac
