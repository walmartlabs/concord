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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class Listeners {

    private static final Logger log = LoggerFactory.getLogger(Listeners.class);

    private static final int MAX_LISTENER_THREADS = 16;

    private final Collection<ProcessEventListener> eventListeners;
    private final Collection<ProcessLogListener> logListeners;
    private final Collection<AuditLogListener> auditLogListeners;

    private final ExecutorService eventListenerExecutor;
    private final ExecutorService logListenerExecutor;
    private final ExecutorService auditLogListenerExecutor;

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

        this.eventListenerExecutor = createExecutor();
        this.logListenerExecutor = createExecutor();
        this.auditLogListenerExecutor = createExecutor();
    }

    @WithTimer
    public void onProcessEvent(List<ProcessEvent> events) {
        eventListeners.forEach(l -> eventListenerExecutor.submit(() -> l.onEvents(events)));
    }

    @WithTimer
    public void onProcessLogAppend(ProcessLogEntry entry) {
        logListeners.forEach(l -> logListenerExecutor.submit(() -> l.onAppend(entry)));
    }

    @WithTimer
    public void onAuditEvent(AuditEvent event) {
        auditLogListeners.forEach(l -> auditLogListenerExecutor.submit(() -> l.onEvent(event)));
    }

    private static ExecutorService createExecutor() {
        ThreadPoolExecutor tpe = new ThreadPoolExecutor(1, MAX_LISTENER_THREADS, 30, TimeUnit.SECONDS, new SynchronousQueue<>());
        tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        return tpe;
    }
}
