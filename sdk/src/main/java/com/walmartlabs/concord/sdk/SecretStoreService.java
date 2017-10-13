package com.walmartlabs.concord.sdk;

public interface SecretStoreService {

    Secret fetch(String instanceId, String secretName, String password) throws ClientException;

    String decryptString(String instanceId, String s) throws ClientException;
}
