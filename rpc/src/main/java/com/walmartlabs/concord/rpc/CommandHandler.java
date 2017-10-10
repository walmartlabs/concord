package com.walmartlabs.concord.rpc;

public interface CommandHandler {

    void onCommand(Command cmd);

    void onError(Throwable t);
}
