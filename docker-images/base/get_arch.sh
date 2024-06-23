#!/usr/bin/env bash

## Mapping between different ways to describe architectures
## (uname -m) vs Docker's way

set -e

case "$(uname -m)" in
  "x86_64")
    echo "amd64"
    ;;
  "aarch64")
    echo "arm64"
    ;;
  *)
    >&2 echo "Unsupported architecture $(uname -m): $(uname -a)"
    exit 1;
    ;;
esac
