package com.walmartlabs.concord.rpc;

public interface SlackService {

    void notify(String instanceId, String channelId, String text) throws ClientException;
}
