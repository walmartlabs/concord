package com.walmartlabs.concord.rpc;

public interface CommandQueue {

    Command take() throws ClientException;
}
