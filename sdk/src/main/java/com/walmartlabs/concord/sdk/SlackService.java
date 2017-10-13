package com.walmartlabs.concord.sdk;

public interface SlackService {

    void notify(String instanceId, String channelId, String text) throws ClientException;
}
