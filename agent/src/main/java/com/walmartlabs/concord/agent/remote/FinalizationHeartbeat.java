package com.walmartlabs.concord.agent.remote;

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

import com.walmartlabs.concord.client2.ApiException;
import com.walmartlabs.concord.client2.ProcessHeartbeatApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * Renews liveness after runner exit. Worker stops it before reporting the final status.
 */
public final class FinalizationHeartbeat implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FinalizationHeartbeat.class);

    private final UUID instanceId;
    private final ProcessHeartbeatApi api;
    private final Duration interval;
    private final Duration pingTimeout;
    private volatile boolean closed;
    private Thread sender;

    public FinalizationHeartbeat(UUID instanceId, ProcessHeartbeatApi api) {
        this(instanceId, api, Duration.ofSeconds(10));
    }

    FinalizationHeartbeat(UUID instanceId, ProcessHeartbeatApi api, Duration interval) {
        this(instanceId, api, interval, Duration.ofSeconds(5));
    }

    FinalizationHeartbeat(UUID instanceId, ProcessHeartbeatApi api, Duration interval, Duration pingTimeout) {
        this.instanceId = instanceId;
        this.api = api;
        this.interval = interval;
        this.pingTimeout = pingTimeout;
    }

    public synchronized void start() {
        if (closed || sender != null) {
            return;
        }
        sender = Thread.ofVirtual().name("finalization-heartbeat-" + instanceId).start(this::ping);
    }

    private void ping() {
        while (!closed && !Thread.currentThread().isInterrupted()) {
            try {
                RemoteOperations.call(pingTimeout, () -> {
                    api.pingProcess(instanceId);
                    return null;
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (closed) {
                    return;
                }
                if (e instanceof ApiException apiError && (apiError.getCode() == 401 || apiError.getCode() == 403)) {
                    // Expected after the server commits a terminal status or cancels the process.
                    closed = true;
                    log.debug("finalization heartbeat [{}] -> authorization rejected: HTTP {}", instanceId, apiError.getCode());
                    return;
                }
                log.warn("finalization heartbeat [{}] -> {}", instanceId, e.getMessage());
            }
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        Thread worker;
        synchronized (this) {
            closed = true;
            worker = sender;
        }
        if (worker != null) {
            worker.interrupt();
            RemoteOperations.join(worker);
        }
    }
}
