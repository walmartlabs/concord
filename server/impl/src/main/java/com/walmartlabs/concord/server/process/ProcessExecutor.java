package com.walmartlabs.concord.server.process;

public interface ProcessExecutor {

    void run(Payload payload, ProcessExecutorCallback callback) throws ProcessExecutorException;

    void cancel(String instanceId);
}
