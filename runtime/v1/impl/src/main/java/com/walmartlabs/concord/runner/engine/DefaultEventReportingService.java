package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.client2.ApiClientConfiguration;
import com.walmartlabs.concord.client2.ApiClientFactory;
import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.client2.ProcessEventsApi;
import io.takari.bpm.state.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultEventReportingService implements EventReportingService {

    private static final Logger log = LoggerFactory.getLogger(DefaultEventReportingService.class);

    private final ApiClientFactory apiClientFactory;
    private final int maxBatchSize;
    private final Object batchLock = new Object();
    private final ScheduledExecutorService flushScheduler;
    private final HashMap<ReportingContext, Queue<ProcessEventRequest>> eventQueues;

    public DefaultEventReportingService(ApiClientFactory apiClientFactory,
                                        int batchSize,
                                        int batchFlushInterval) {
        this.apiClientFactory = apiClientFactory;
        this.maxBatchSize = batchSize;
        this.eventQueues = new HashMap<>(1);
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor();

        flushScheduler.scheduleAtFixedRate(new FlushTimer(this), batchFlushInterval, batchFlushInterval, TimeUnit.SECONDS);
    }

    @Override
    public ProcessInstance onFinalize(ProcessInstance state) {
        flushScheduler.shutdown();
        flush();
        return state;
    }

    private class ReportingBatch {
        private final ReportingContext reportingContext;
        private final List<ProcessEventRequest> events;

        public ReportingBatch(ReportingContext reportingContext, List<ProcessEventRequest> events) {
            this.reportingContext = reportingContext;
            this.events = events;
        }

        public void send() {
            try {
                getProcessEventsApi(reportingContext.sessionToken).batchEvent(reportingContext.instanceId, events);
            } catch (ApiException e) {
                log.warn("Error while sending batch of {} event{} to the server: {}",
                        events.size(), events.isEmpty() ? "" : "s", e.getMessage());
            }
        }
    }

    ProcessEventsApi getProcessEventsApi(String sessionToken) {
        return new ProcessEventsApi(apiClientFactory.create(
                ApiClientConfiguration.builder()
                        .sessionToken(sessionToken)
                        .build()));
    }

    /**
     * Event source context. Basically a combo of instance ID and session token.
     * Intended to be used as a key for a map of context -> queue of events
     */
    static class ReportingContext {
        private final UUID instanceId;
        private final String  sessionToken;

        public ReportingContext(UUID instanceId, String sessionToken) {
            this.instanceId = instanceId;
            this.sessionToken = sessionToken;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ReportingContext that = (ReportingContext) o;

            if (!instanceId.equals(that.instanceId)) return false;
            return sessionToken.equals(that.sessionToken);
        }

        @Override
        public int hashCode() {
            int result = instanceId.hashCode();
            result = 31 * result + sessionToken.hashCode();
            return result;
        }
    }

    @Override
    public void report(ProcessEventRequest req, UUID instanceId, String sessionToken) {
        if (maxBatchSize > 1) {
            batch(req, instanceId, sessionToken);
        } else {
            sendSingle(req, instanceId, sessionToken);
        }
    }

    void batch(ProcessEventRequest req, UUID instanceId, String sessionToken) {
        Queue<ProcessEventRequest> queue;

        synchronized (batchLock) {
            queue = eventQueues.computeIfAbsent(new ReportingContext(instanceId, sessionToken), ctx -> new ArrayDeque<>(maxBatchSize));
            queue.add(req);
        }

        if (queue.size() >= maxBatchSize) {
            flush();
        }
    }

    void sendSingle(ProcessEventRequest req, UUID instanceId, String sessionToken) {
        try {
            ProcessEventsApi client = getProcessEventsApi(sessionToken);
            client.event(instanceId, req);
        } catch (ApiException e) {
            log.warn("error while sending an event to the server: {}", e.getMessage());
        }
    }

    /**
     * Flushes all queued events across all reporting contexts.
     */
    void flush() {
        ReportingBatch eventBatch = takeBatch();

        while (eventBatch != null && !eventBatch.events.isEmpty()) {
            eventBatch.send();
            eventBatch = takeBatch();
        }
    }

    /**
     * @return batch of up-to {@link #maxBatchSize} queued process events
     */
    private ReportingBatch takeBatch() {
        ReportingContext ctx;
        List<ProcessEventRequest> batch = new ArrayList<>(maxBatchSize);

        synchronized (batchLock) {
            if (eventQueues.isEmpty()) {
                return null; // nothing to report
            }

            Optional<ReportingContext> optionalCtx = eventQueues.entrySet().stream()
                    .findFirst()
                    .map(Map.Entry::getKey);

            if (optionalCtx.isEmpty()) {
                // that's odd, should've been cleaned up already
                eventQueues.clear();
                return null;
            }

            ctx = optionalCtx.get();

            Queue<ProcessEventRequest> eventQueue = eventQueues.get(ctx);

            for (int i = 0; i < maxBatchSize; i++) {
                if (eventQueue.isEmpty()) {
                    eventQueues.remove(ctx);
                    break;
                }

                batch.add(eventQueue.poll());
            }
        }

        return new ReportingBatch(ctx, batch);
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
