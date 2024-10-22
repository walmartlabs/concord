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

import com.walmartlabs.concord.client2.ProcessEventRequest;
import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.svm.Frame;
import com.walmartlabs.concord.svm.Runtime;
import com.walmartlabs.concord.svm.State;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultEventReportingService implements EventReportingService {

    private final BlockingQueue<ProcessEventRequest> eventQueue;
    private final int maxBatchSize;
    private final Object batchLock = new Object();
    private final ScheduledExecutorService flushScheduler;
    private final ProcessEventWriter eventWriter;

    @Inject
    public DefaultEventReportingService(ProcessConfiguration processConfiguration,
                                        ProcessEventWriter eventWriter) {
        this.maxBatchSize = processConfiguration.events().batchSize();
        this.eventQueue = initializeQueue(maxBatchSize);
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor();

        int period = processConfiguration.events().batchFlushInterval();
        flushScheduler.scheduleAtFixedRate(new FlushTimer(this), period, period, TimeUnit.SECONDS);

        this.eventWriter = eventWriter;
    }

    private static BlockingQueue<ProcessEventRequest> initializeQueue(int maxBatchSize) {
        if (maxBatchSize <= 0) {
            throw new IllegalArgumentException("Invalid event batch size '" + maxBatchSize + "'. Must be greater than zero.");
        }

        return new LinkedBlockingQueue<>();
    }

    @Override
    public synchronized void report(ProcessEventRequest req) {
        if (req == null) {
            return;
        }

        // avoid modification while a flush may be occurring from another thread
        synchronized (batchLock) {
            eventQueue.add(req);
        }

        // Don't allow batch to grow larger than max batch size
        // This is why the method is synchronized. If not, another thread may add
        // one-too-many items to the batch before this flush finishes.
        if (eventQueue.size() >= maxBatchSize) {
            flush();
        }
    }

    @Override
    public void afterProcessEnds(Runtime runtime, State state, Frame lastFrame) {
        flushScheduler.shutdown();
        flush();
    }

    synchronized void flush() {
        List<ProcessEventRequest> eventBatch = takeBatch();

        while (!eventBatch.isEmpty()) {
            send(eventBatch);
            eventBatch = takeBatch();
        }
    }

    private void send(List<ProcessEventRequest> eventBatch) {
        if (eventBatch.size() == 1) {
            eventWriter.write(eventBatch.get(0));
        } else {
            eventWriter.write(eventBatch);
        }
    }

    /**
     * @return batch of up-to {@link #maxBatchSize} queued process events
     */
    private List<ProcessEventRequest> takeBatch() {
        List<ProcessEventRequest> batch = new ArrayList<>(maxBatchSize);

        synchronized (batchLock) { // avoid draining while an element may be added
            eventQueue.drainTo(batch, maxBatchSize);
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
