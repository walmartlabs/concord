package com.walmartlabs.concord.runner;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ApiClientConfiguration;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ProcessHeartbeatApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.UUID;

@Named
@Singleton
public class ProcessHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ProcessHeartbeat.class);

    private static final long HEARTBEAT_INTERVAL = 10000;

    private final ApiClientFactory apiClientFactory;
    private Thread worker;

    @Inject
    public ProcessHeartbeat(ApiClientFactory apiClientFactory) {
        this.apiClientFactory = apiClientFactory;
    }

    public synchronized void start(UUID instanceId, String sessionToken) {
        if (worker != null) {
            throw new IllegalArgumentException("Heartbeat worker is already running");
        }

        worker = new Thread(() -> {
            log.info("start ['{}'] -> running every {}ms", instanceId, HEARTBEAT_INTERVAL);

            ApiClient client = apiClientFactory.create(ApiClientConfiguration.builder()
                    .sessionToken(sessionToken)
                    .build());

            ProcessHeartbeatApi processHeartbeatApi = new ProcessHeartbeatApi(client);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    processHeartbeatApi.ping(instanceId);
                } catch (Exception e) {
                    log.warn("run -> heartbeat error: {}", e.getMessage());
                }

                try {
                    Thread.sleep(HEARTBEAT_INTERVAL); // NOSONAR
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("start ['{}'] -> stopped", instanceId);
        }, "process-heartbeat");

        worker.start();
    }
}
