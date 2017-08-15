package com.walmartlabs.concord.rpc;

public interface SecretStoreService {

    String decryptString(String instanceId, String s) throws ClientException;
}
