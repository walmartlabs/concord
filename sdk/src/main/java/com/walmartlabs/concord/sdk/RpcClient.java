package com.walmartlabs.concord.sdk;

public interface RpcClient {

    KvService getKvService();

    SecretStoreService getSecretStoreService();

    EventService getEventService();

    SlackService getSlackService();
}
