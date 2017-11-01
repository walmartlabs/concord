package com.walmartlabs.concord.plugins.kv;

import com.walmartlabs.concord.sdk.InjectVariable;
import com.walmartlabs.concord.sdk.KvService;
import com.walmartlabs.concord.sdk.RpcClient;
import com.walmartlabs.concord.sdk.Task;

import javax.inject.Inject;
import javax.inject.Named;

@Named("kv")
public class KvTask implements Task {

    private final KvService kvService;

    @Inject
    public KvTask(RpcClient rpcClient) {
        this.kvService = rpcClient.getKvService();
    }

    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        kvService.remove(instanceId, key);
    }

    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        kvService.putString(instanceId, key, value);
    }

    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.getString(instanceId, key);
    }

    public void putLong(@InjectVariable("txId") String instanceId, String key, Long value) throws Exception {
        kvService.putLong(instanceId, key, value);
    }

    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return incLong(instanceId, key);
    }

    public long incLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.incLong(instanceId, key);
    }

    public Long getLong(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return kvService.getLong(instanceId, key);
    }
}
