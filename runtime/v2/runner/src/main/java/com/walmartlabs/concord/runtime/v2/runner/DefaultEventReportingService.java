package com.walmartlabs.concord.runtime.v2.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.client2.ApiClient;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import com.walmartlabs.concord.runtime.common.injector.InstanceId;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultEventReportingService implements EventReportingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventReportingService.class);

    private final InstanceId instanceId;
    private final ProcessEventsApi processEventsApi;
    private final Queue<ProcessEventRequest> eventQueue;
    private final int maxBatchSize;
    private final Object batchLock = new Object();
    private final ScheduledExecutorService flushScheduler;

    @Inject
    public DefaultEventReportingService(InstanceId instanceId,
                                        ProcessConfiguration processConfiguration,
                                        ApiClient apiClient) {
        this.instanceId = instanceId;
        this.processEventsApi = new ProcessEventsApi(apiClient);
        this.maxBatchSize = processConfiguration.events().batchSize();
        this.eventQueue = new ArrayDeque<>(maxBatchSize);
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor();

        int period = processConfiguration.events().batchFlushInterval();
        flushScheduler.scheduleAtFixedRate(new FlushTimer(this), period, period, TimeUnit.SECONDS);
    }

    @Override
    public void report(ProcessEventRequest req) {
        synchronized (batchLock) {
            eventQueue.add(req);
        }

        if (eventQueue.size() >= maxBatchSize) {
            flush();
        }
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        flushScheduler.shutdown();
        flush();
    }

    ProcessEventsApi getProcessEventsApi() {
        return processEventsApi;
    }

    void flush() {
        List<ProcessEventRequest> eventBatch = takeBatch();

        while (!eventBatch.isEmpty()) {
            if (eventBatch.size() == 1) {
                sendSingle(eventBatch.get(0));
            } else {
                sendBatch(eventBatch);
            }

            eventBatch = takeBatch();
        }
    }

    private void sendBatch(List<ProcessEventRequest> eventBatch) {
        try {
            getProcessEventsApi().batchEvent(instanceId.getValue(), eventBatch);
        } catch (ApiException e) {
            log.warn("Error while sending batch of {} event{} to the server: {}",
                    eventBatch.size(), eventBatch.isEmpty() ? "" : "s", e.getMessage());
        }
    }

    private void sendSingle(ProcessEventRequest req) {
        try {
            getProcessEventsApi().event(instanceId.getValue(), req);
        } catch (ApiException e) {
            log.warn("error while sending an event to the server: {}", e.getMessage());
        }
    }

    /**
     * @return batch of up-to {@link #maxBatchSize} queued process events
     */
    private List<ProcessEventRequest> takeBatch() {
        List<ProcessEventRequest> batch = new ArrayList<>(maxBatchSize);

        synchronized (batchLock) {
            for (int i = 0; i < maxBatchSize; i++) {
                if (this.eventQueue.isEmpty()) {
                    break;
                }

                batch.add(this.eventQueue.poll());
            }
        }

        return batch;
    }

    private static class FlushTimer extends TimerTask {
        private final DefaultEventReportingService reportingService;

        public FlushTimer(DefaultEventReportingService reportingService) {
            this.reportingService = reportingService;
        }

        @Override
        public void run() {
            reportingService.flush();
        }
    }

}
