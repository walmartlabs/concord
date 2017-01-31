package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ProcessStatus;

public interface ProcessExecutorCallback {

    void onStart(String instanceId);

    /**
     * A process has changed its status
     * @param instanceId
     * @param status new status
     */
    void onStatusChange(String instanceId, ProcessStatus status);

    /**
     * The server received an updated state of a process.
     * @param instanceId
     */
    void onUpdate(String instanceId);
}
