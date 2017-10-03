package com.walmartlabs.concord.rpc;

import com.walmartlabs.concord.common.secret.Secret;

public interface SecretStoreService {

    Secret fetch(String instanceId, String secretName, String password) throws ClientException;

    String decryptString(String instanceId, String s) throws ClientException;
}
