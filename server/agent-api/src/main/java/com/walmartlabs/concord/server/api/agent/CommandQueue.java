package com.walmartlabs.concord.server.api.agent;

public interface CommandQueue {

    Command take() throws ClientException;
}
