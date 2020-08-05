package com.walmartlabs.concord.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.sdk.audit.AuditEvent;
import com.walmartlabs.concord.server.sdk.audit.AuditLogListener;
import com.walmartlabs.concord.server.sdk.events.ProcessEvent;
import com.walmartlabs.concord.server.sdk.events.ProcessEventListener;
import com.walmartlabs.concord.server.sdk.log.ProcessLogEntry;
import com.walmartlabs.concord.server.sdk.log.ProcessLogListener;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

@Named
@Singleton
public class Listeners {

    private static final Logger log = LoggerFactory.getLogger(Listeners.class);

    private static final int MAX_LISTENER_THREADS = 16;

    private static final Duration MAX_LISTENER_TIME = Duration.ofSeconds(3);

    private final Collection<ProcessEventListener> eventListeners;
    private final Collection<ProcessLogListener> logListeners;
    private final Collection<AuditLogListener> auditLogListeners;

    private final ForkJoinPool eventListenerPool;
    private final ForkJoinPool logListenerPool;
    private final ForkJoinPool auditLogListenerPool;

    @Inject
    public Listeners(Collection<ProcessEventListener> eventListeners,
                     Collection<ProcessLogListener> logListeners,
                     Collection<AuditLogListener> auditLogListeners) {

        this.eventListeners = eventListeners;
        eventListeners.forEach(l -> log.info("Using process event listener: {}", l));

        this.logListeners = logListeners;
        logListeners.forEach(l -> log.info("Using process log listener: {}", l));

        this.auditLogListeners = auditLogListeners;
        auditLogListeners.forEach(l -> log.info("Using audit log listener: {}", l));

        this.eventListenerPool = new ForkJoinPool(MAX_LISTENER_THREADS);
        this.logListenerPool = new ForkJoinPool(MAX_LISTENER_THREADS);
        this.auditLogListenerPool = new ForkJoinPool(MAX_LISTENER_THREADS);
    }

    @WithTimer
    public void onProcessEvent(List<ProcessEvent> events) {
        ForkJoinTask<?> task = eventListenerPool.submit(() -> {
            eventListeners.parallelStream().forEach(l -> l.onEvents(events));
        });

        waitFor(task);
    }

    @WithTimer
    public void onProcessLogAppend(ProcessLogEntry entry) {
        ForkJoinTask<?> task = logListenerPool.submit(() -> {
            logListeners.parallelStream().forEach(l -> l.onAppend(entry));
        });

        waitFor(task);
    }

    @WithTimer
    public void onAuditEvent(AuditEvent event) {
        ForkJoinTask<?> task = auditLogListenerPool.submit(() -> {
            auditLogListeners.parallelStream().forEach(l -> l.onEvent(event));
        });

        waitFor(task);
    }

    private static void waitFor(ForkJoinTask<?> task) {
        try {
            task.get(MAX_LISTENER_TIME.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }
}
