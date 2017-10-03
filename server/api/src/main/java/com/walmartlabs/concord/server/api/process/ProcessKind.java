package com.walmartlabs.concord.server.api.process;

public enum  ProcessKind {

    /**
     * Regular process.
     */
    DEFAULT,

    /**
     * Process running an failure-handling flow of a parent process.
     * Created when a parent process crashes, exits with an error or
     * otherwise fails.
     */
    FAILURE_HANDLER,

    /**
     * Process running a cancel-handling flow of a parent process.
     * Created when a user cancels a process.
     */
    CANCEL_HANDLER
}
