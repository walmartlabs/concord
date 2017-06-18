package com.walmartlabs.concord.rpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.util.concurrent.TimeUnit;

public class RunnerApiClient {

    private final ManagedChannel channel;
    private final KvService kvService;
    private final SecretStoreService secretStoreService;
    private final EventService eventService;
    private final SlackService slackService;

    public RunnerApiClient(String instanceId, String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();

        this.kvService = new KvServiceImpl(instanceId, channel);
        this.secretStoreService = new SecretStoreServiceImpl(instanceId, channel);
        this.eventService = new EventServiceImpl(instanceId, channel);
        this.slackService = new SlackServiceImpl(instanceId, channel);
    }

    public void stop() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public KvService getKvService() {
        return kvService;
    }

    public SecretStoreService getSecretStoreService() {
        return secretStoreService;
    }

    public EventService getEventService() {
        return eventService;
    }

    public SlackService getSlackService() {
        return slackService;
    }
}
