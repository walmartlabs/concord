package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.rpc.KvService;
import com.walmartlabs.concord.rpc.RunnerApiClient;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class RpcClient {

    private final RunnerApiClient client;

    public RpcClient() {
        String instanceid = System.getProperty("instanceId");
        String host = System.getProperty("rpc.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("rpc.server.port", "8101"));

        this.client = new RunnerApiClient(instanceid, host, port);
    }

    public KvService getKvService() {
        return client.getKvService();
    }
}
