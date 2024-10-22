package com.walmartlabs.concord.runtime.v2.runner;

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;

public class DefaultProcessEventWriter implements ProcessEventWriter {

    private static final Logger log = LoggerFactory.getLogger(DefaultProcessEventWriter.class);

    private final ProcessEventsApi processEventsApi;

    private final InstanceId instanceId;

    @Inject
    public DefaultProcessEventWriter(InstanceId instanceId, ApiClient apiClient) {
        this.instanceId = instanceId;
        this.processEventsApi = new ProcessEventsApi(apiClient);
    }

    @Override
    public void write(List<ProcessEventRequest> eventBatch) {
        try {
            processEventsApi.batchEvent(instanceId.getValue(), eventBatch);
        } catch (ApiException e) {
            log.warn("Error while sending batch of {} event{} to the server: {}",
                    eventBatch.size(), eventBatch.isEmpty() ? "" : "s", e.getMessage());
        }
    }

    @Override
    public void write(ProcessEventRequest req) {
        try {
            processEventsApi.event(instanceId.getValue(), req);
        } catch (ApiException e) {
            log.warn("error while sending an event to the server: {}", e.getMessage());
        }
    }
}
