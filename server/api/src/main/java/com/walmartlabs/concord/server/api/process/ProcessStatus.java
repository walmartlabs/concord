package com.walmartlabs.concord.server.api.process;

public enum ProcessStatus {

    ENQUEUED,
    STARTING,
    RUNNING,
    SUSPENDED,
    RESUMING,
    FINISHED,
    FAILED
}
