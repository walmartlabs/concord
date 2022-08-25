#!/usr/bin/env bash

set -e

JDK_18_AMD64="https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u332-b09/OpenJDK8U-jdk_x64_linux_hotspot_8u332b09.tar.gz"
JDK_18_ARM64="https://cdn.azul.com/zulu-embedded/bin/zulu8.62.0.19-ca-jdk8.0.332-linux_aarch64.tar.gz"
JDK_11_AMD64="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_x64_linux_hotspot_11.0.15_10.tar.gz"
JDK_11_ARM64="https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.15%2B10/OpenJDK11U-jdk_aarch64_linux_hotspot_11.0.15_10.tar.gz"
JDK_17_AMD64="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_x64_linux_hotspot_17.0.3_7.tar.gz"
JDK_17_ARM64="https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.3%2B7/OpenJDK17U-jdk_aarch64_linux_hotspot_17.0.3_7.tar.gz"

case "${JDK_VERSION}-${TARGETARCH}" in
  "1.8-amd64")
    echo ${JDK_18_AMD64}
    ;;
  "1.8-arm64")
    echo ${JDK_18_ARM64}
    ;;
  "11-amd64")
    echo ${JDK_11_AMD64}
    ;;
  "11-arm64")
    echo ${JDK_11_ARM64}
    ;;
  "17-amd64")
    echo ${JDK_17_AMD64}
    ;;
  "17-arm64")
    echo ${JDK_17_ARM64}
    ;;
  *)
    >&2 echo "Unsupported JDK ${JDK_VERSION}-${TARGETARCH}"
    exit 1;
    ;;
esac
