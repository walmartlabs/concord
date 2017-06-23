package com.walmartlabs.concord.rpc;

import io.grpc.ManagedChannel;

import java.util.concurrent.TimeUnit;

public class SlackServiceImpl implements SlackService {

    private static final long REQUEST_TIMEOUT = 5000;

    private final String instanceId;
    private final ManagedChannel channel;

    public SlackServiceImpl(String instanceId, ManagedChannel channel) {
        this.instanceId = instanceId;
        this.channel = channel;
    }

    @Override
    public void notify(String channelId, String text) throws ClientException {
        TSlackNotificationServiceGrpc.TSlackNotificationServiceBlockingStub blockingStub = TSlackNotificationServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(REQUEST_TIMEOUT, TimeUnit.MILLISECONDS);

        TSlackNotificationResponse result = blockingStub.notify(TSlackNotificationRequest.newBuilder()
                .setInstanceId(instanceId)
                .setSlackChannelId(channelId)
                .setNotificationText(text)
            .build());

        if(!result.getOk()) {
            throw new ClientException("Error while trying to send slack notification: " + result.getError());
        }
    }
}
