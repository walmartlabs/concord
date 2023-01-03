package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import java.util.concurrent.Executors;

public class CommandHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);

    private static final long ERROR_DELAY = 5000;

    private final ExecutorService executor;

    private final UUID agentId;
    private final QueueClient queueClient;
    private final long pollInterval;
    private final CancelHandler cancelHandler;

    public CommandHandler(String agentId,
                          QueueClient queueClient,
                          long pollInterval,
                          CancelHandler cancelHandler) {

        this.executor = Executors.newCachedThreadPool();

        this.agentId = UUID.fromString(agentId);
        this.queueClient = queueClient;
        this.pollInterval = pollInterval;
        this.cancelHandler = cancelHandler;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CommandResponse cmd = take();
                if (cmd == null) {
                    sleep(pollInterval);
                    continue;
                }

                executor.submit(() -> execute(cmd));
            } catch (Exception e) {
                log.error("run -> error while processing a command: {}", e.getMessage(), e);
                sleep(ERROR_DELAY);
            }
        }
    }

    private CommandResponse take() throws Exception {
        try {
            return queueClient.<CommandResponse>request(new CommandRequest(agentId)).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void execute(CommandResponse cmd) {
        log.info("execute -> got a command: {}", cmd);

        CommandResponse.CommandType type = cmd.getType();
        if (type == CommandResponse.CommandType.CANCEL_JOB) {
            UUID instanceId = UUID.fromString((String) cmd.getPayload().get("instanceId"));
            cancelHandler.cancel(instanceId);
        } else {
            log.warn("execute -> unsupported command type: {}", type);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public interface CancelHandler {
        void cancel(UUID instanceId);
    }
}
