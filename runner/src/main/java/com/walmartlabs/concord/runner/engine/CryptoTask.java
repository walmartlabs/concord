package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import javax.inject.Inject;
import javax.inject.Named;

@Named("crypto")
public class CryptoTask implements Task {

    private final RpcClient rpcClient;

    @Inject
    public CryptoTask(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    public String decryptString(String s) throws Exception {
        return rpcClient.getSecretStoreService().decryptString(s);
    }
}
