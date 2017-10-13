package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class RpcClientImpl implements RpcClient {

    private final RunnerApiClient client;

    @Inject
    public RpcClientImpl(RpcConfiguration cfg) {
        this.client = new RunnerApiClient(cfg.getServerHost(), cfg.getServerPort());
    }

    @Override
    public KvService getKvService() {
        return client.getKvService();
    }

    @Override
    public SecretStoreService getSecretStoreService() {
        return client.getSecretStoreService();
    }

    @Override
    public EventService getEventService() {
        return client.getEventService();
    }

    @Override
    public SlackService getSlackService() {
        return client.getSlackService();
    }
}
