package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.RpcConfiguration;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class RpcClient {

    private final RunnerApiClient client;

    @Inject
    public RpcClient(RpcConfiguration cfg) {
        this.client = new RunnerApiClient(cfg.getServerHost(), cfg.getServerPort());
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
