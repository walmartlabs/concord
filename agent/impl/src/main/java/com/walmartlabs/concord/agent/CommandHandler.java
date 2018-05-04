package com.walmartlabs.concord.agent;

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

import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.server.api.CommandType;
import com.walmartlabs.concord.server.api.agent.CommandEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class CommandHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandHandler.class);
    private static final long ERROR_DELAY = 5000;

    private final CommandQueueClient client;
    private final ExecutionManager executionManager;
    private final ExecutorService executor;

    public CommandHandler(CommandQueueClient client, ExecutionManager executionManager, ExecutorService executor) {
        this.client = client;
        this.executionManager = executionManager;
        this.executor = executor;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                CommandEntry cmd = client.take();
                if (cmd == null) {
                    continue;
                }

                executor.submit(() -> execute(cmd));
            } catch (Exception e) {
                log.error("run -> command process error: {}", e.getMessage(), e);
                sleep(ERROR_DELAY);
            }
        }
    }

    private void execute(CommandEntry cmd) {
        log.info("execute -> got a command: {}", cmd);

        if (cmd.getType() == CommandType.CANCEL_JOB) {
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
