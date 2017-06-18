package com.walmartlabs.concord.rpc;

public interface SlackService {

    void notify(String channelId, String text) throws ClientException;
}
