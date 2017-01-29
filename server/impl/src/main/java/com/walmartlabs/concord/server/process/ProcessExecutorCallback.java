package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ProcessStatus;

public interface ProcessExecutorCallback {

    /**
     * A process has changed its status
     * @param payload
     * @param status new status
     */
    void onStatusChange(Payload payload, ProcessStatus status);

    /**
     * The server received an updated state of a process.
     * @param payload
     */
    void onUpdate(Payload payload);
}
