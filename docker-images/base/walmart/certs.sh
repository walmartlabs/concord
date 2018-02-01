#!/bin/bash
set -e

URLS="https://pki.wal-mart.com/pki/CAs/Walmart/WalmartRootCA.crt
https://pki.wal-mart.com/pki/CAs/Walmart/WalmartRootCA-SHA256.crt
https://pki.wal-mart.com/pki/CAs/Walmart/WalmartIssuingCA-TLS-02-SHA256.crt"

for url in $URLS; do
    wget --no-check-certificate -P /etc/pki/ca-trust/source/anchors/ $url;
done

update-ca-trust force-enable
update-ca-trust extract
