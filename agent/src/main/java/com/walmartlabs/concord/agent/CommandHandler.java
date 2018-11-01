package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.message.CommandRequest;
import com.walmartlabs.concord.server.queueclient.message.CommandResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CommandHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final long ERROR_DELAY = 5000;

    private final UUID agentId;
    private final QueueClient queueClient;
    private final ExecutionManager executionManager;
    private final ExecutorService executor;

    private final long pollInterval;

    public CommandHandler(String agentId, QueueClient queueClient,
                          ExecutionManager executionManager, ExecutorService executor,
                          long pollInterval) {
        this.agentId = UUID.fromString(agentId);
        this.queueClient = queueClient;
        this.executionManager = executionManager;
        this.executor = executor;
        this.pollInterval = pollInterval;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CommandResponse cmd = take();
                if (cmd != null) {
                    executor.submit(() -> execute(cmd));
                } else {
                    sleep(pollInterval);
                }
            } catch (Exception e) {
                log.error("run -> command process error: {}", e.getMessage(), e);
                sleep(ERROR_DELAY);
            }
        }
    }

    private CommandResponse take() throws Exception {
        return queueClient.<CommandResponse>request(new CommandRequest(agentId)).get();
    }

    private void execute(CommandResponse cmd) {
        log.info("execute -> got a command: {}", cmd);

        if (cmd.getType() == CommandResponse.CommandType.CANCEL_JOB) {
            executionManager.cancel(UUID.fromString((String)cmd.getPayload().get("instanceId")));
        } else {
            log.warn("execute -> unsupported command type: " + cmd.getClass());
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
