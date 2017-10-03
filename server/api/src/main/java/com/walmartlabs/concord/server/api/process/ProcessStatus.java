package com.walmartlabs.concord.server.api.process;

public enum ProcessStatus {

    PREPARING,
    ENQUEUED,
    STARTING,
    RUNNING,
    SUSPENDED,
    RESUMING,
    FINISHED,
    FAILED,
    CANCELLED
}
