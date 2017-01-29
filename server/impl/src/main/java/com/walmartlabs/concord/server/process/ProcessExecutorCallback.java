package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.server.api.process.ProcessStatus;

public interface ProcessExecutorCallback {

    void onStatusChange(Payload payload, ProcessStatus status);
}
