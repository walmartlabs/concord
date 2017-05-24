package com.walmartlabs.concord.runner.engine;

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

    public void remove(String key) throws Exception {
        rpc.getKvService().remove(key);
    }

    public void putString(String key, String value) throws Exception {
        rpc.getKvService().put(key, value);
    }

    public String getString(String key) throws Exception {
        return rpc.getKvService().get(key);
    }

    public long inc(String key) throws Exception {
        return rpc.getKvService().inc(key);
    }
}
