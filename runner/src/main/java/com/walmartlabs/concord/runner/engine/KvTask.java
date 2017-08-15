package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.InjectVariable;
import com.walmartlabs.concord.common.Task;

import javax.inject.Inject;
import javax.inject.Named;

@Named("kv")
public class KvTask implements Task {

    private final RpcClient rpc;

    @Inject
    public KvTask(RpcClient rpc) {
        this.rpc = rpc;
    }

    public void remove(@InjectVariable("txId") String instanceId, String key) throws Exception {
        rpc.getKvService().remove(instanceId, key);
    }

    public void putString(@InjectVariable("txId") String instanceId, String key, String value) throws Exception {
        rpc.getKvService().put(instanceId, key, value);
    }

    public String getString(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return rpc.getKvService().get(instanceId, key);
    }

    public long inc(@InjectVariable("txId") String instanceId, String key) throws Exception {
        return rpc.getKvService().inc(instanceId, key);
    }
}
