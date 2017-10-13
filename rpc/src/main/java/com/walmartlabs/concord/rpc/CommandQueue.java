package com.walmartlabs.concord.rpc;

import com.walmartlabs.concord.sdk.ClientException;

public interface CommandQueue {

    void stream(CommandHandler handler) throws ClientException;
}
