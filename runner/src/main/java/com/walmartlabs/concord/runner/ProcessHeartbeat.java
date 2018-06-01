package com.walmartlabs.concord.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.runner.engine.RpcClientImpl;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.ProcessHeartbeatApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.UUID;

@Named
@Singleton
public class ProcessHeartbeat {

    private static final Logger log = LoggerFactory.getLogger(ProcessHeartbeat.class);

    private static final long HEARTBEAT_INTERVAL = 10000;

    private final ProcessHeartbeatApi client;
    private Thread worker;

    @Inject
    public ProcessHeartbeat(Configuration cfg) throws IOException {
        this.client = new ProcessHeartbeatApi(createClient(cfg));
    }

    public synchronized void start(UUID instanceId) {
        if (worker != null) {
            throw new IllegalArgumentException("Heartbeat worker is already running");
        }

        worker = new Thread(() -> {
            log.info("start ['{}'] -> running every {}ms", instanceId, HEARTBEAT_INTERVAL);

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    client.ping(instanceId);
                } catch (Exception e) {
                    log.warn("run -> heartbeat error: {}", e.getMessage());
                }

                try {
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            log.info("start ['{}'] -> stopped", instanceId);
        }, "process-heartbeat");

        worker.start();
    }

    private static ApiClient createClient(Configuration cfg) throws IOException {
        ApiClient client = new ConcordApiClient();
        client.setTempFolderPath(IOUtils.createTempDir("runner-client").toString());
        client.setBasePath(cfg.getServerApiBaseUrl());
        client.setApiKey(cfg.getApiKey());
        client.setReadTimeout(cfg.getReadTimeout());
        client.setConnectTimeout(cfg.getConnectTimeout());
        return client;
    }
}
