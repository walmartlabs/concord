package com.walmartlabs.concord.rpc;

public interface SecretStoreService {

    String decryptString(String s) throws ClientException;
}
