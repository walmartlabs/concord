package com.walmartlabs.concord.rpc;

public interface CommandQueue {

    void stream(CommandHandler handler) throws ClientException;
}
