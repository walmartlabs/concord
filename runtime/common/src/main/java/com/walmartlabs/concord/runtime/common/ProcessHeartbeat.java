package com.walmartlabs.concord.runtime.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.client2.ProcessHeartbeatApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.UUID;

public class ProcessHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ProcessHeartbeat.class);

    private static final long HEARTBEAT_INTERVAL = 10000;

    private final ProcessHeartbeatApi processHeartbeatApi;
    private final UUID instanceId;
    private final long maxNoHeartbeatInterval;
    private Thread worker;

    public ProcessHeartbeat(ApiClient client, UUID instanceId, long maxNoHeartbeatInterval) {
        this.processHeartbeatApi = new ProcessHeartbeatApi(client);
        this.instanceId = instanceId;
        this.maxNoHeartbeatInterval = maxNoHeartbeatInterval;
    }

    public synchronized void start() {
        if (worker != null) {
            throw new IllegalArgumentException("Heartbeat worker is already running");
        }

        worker = new Thread(() -> {
            log.debug("start ['{}'] -> running every {}ms, max interval: {}ms", instanceId, HEARTBEAT_INTERVAL, maxNoHeartbeatInterval);

            boolean prevPingFailed = false;
            long lastSuccessPing = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processHeartbeatApi.pingProcess(instanceId);
                    lastSuccessPing = System.currentTimeMillis();
                    if (prevPingFailed) {
                        log.info("heartbeat: ok");
                    }
                    prevPingFailed = false;
                } catch (Exception e) {
                    prevPingFailed = true;
                    log.warn("heartbeat: error: {}, last successful at {}", e.getMessage(), new Date(lastSuccessPing));

                    // check if we hadn't had a successful heartbeat request in a while
                    long pingInterval = System.currentTimeMillis() - lastSuccessPing;
                    if (pingInterval > maxNoHeartbeatInterval) {
                        log.error("No heartbeat for more than {}ms, terminating process...", pingInterval);
                        System.exit(1);
                    }
                }

                try {
                    Thread.sleep(HEARTBEAT_INTERVAL); // NOSONAR
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.debug("start ['{}'] -> stopped", instanceId);
        }, "process-heartbeat");

        worker.start();
    }

    public synchronized void stop() {
        if (worker == null) {
            return;
        }

        worker.interrupt();
        worker = null;
    }
}
