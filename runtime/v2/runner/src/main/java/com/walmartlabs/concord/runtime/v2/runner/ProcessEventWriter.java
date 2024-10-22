package com.walmartlabs.concord.runtime.v2.runner;

import com.walmartlabs.concord.client2.ProcessEventRequest;

import java.util.List;

public interface ProcessEventWriter {

    void write(ProcessEventRequest processEventRequest);

    void write(List<ProcessEventRequest> eventBatch);
}
