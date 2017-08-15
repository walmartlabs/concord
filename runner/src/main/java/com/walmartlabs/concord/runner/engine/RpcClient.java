package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.rpc.*;

import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class RpcClient {

    private final RunnerApiClient client;

    public RpcClient() {
        String host = System.getProperty("rpc.server.host", "localhost");
        int port = Integer.parseInt(System.getProperty("rpc.server.port", "8101"));
        this.client = new RunnerApiClient(host, port);
    }

    public KvService getKvService() {
        return client.getKvService();
    }

    public SecretStoreService getSecretStoreService() {
        return client.getSecretStoreService();
    }

    public EventService getEventService() {
        return client.getEventService();
    }

    public SlackService getSlackService() {
        return client.getSlackService();
    }
}
