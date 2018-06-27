package com.walmartlabs.concord.agent.docker;

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


import com.walmartlabs.concord.agent.ExecutionManager;
import com.walmartlabs.concord.common.DockerProcessBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class OrphanSweeper implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(OrphanSweeper.class);

    private static final String[] PS_CMD = {"docker", "ps", "-a",
            "--filter", "label=" + DockerProcessBuilder.CONCORD_TX_ID_LABEL,
            "--format", "{{.Label \"" + DockerProcessBuilder.CONCORD_TX_ID_LABEL + "\"}} {{.ID}}"};

    private static final long RETRY_DELAY = TimeUnit.SECONDS.toMillis(30);

    private final ExecutionManager executionManager;
    private final long period;

    public OrphanSweeper(ExecutionManager executionManager, long period) {
        this.executionManager = executionManager;
        this.period = period;
    }

    @Override
    public void run() {
        log.info("run -> removing orphaned Docker containers...");

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Map<UUID, String> containers = findContainers();
                log.debug("run -> found {} container(s)...", containers.size());

                for (Map.Entry<UUID, String> c : containers.entrySet()) {
                    UUID txId = c.getKey();
                    if (executionManager.isRunning(txId)) {
                        continue;
                    }

                    String cId = c.getValue();
                    log.warn("run -> found an orphaned container {} (process {}), attempting to kill...", cId, txId);
                    killContainer(cId);
                }

                sleep(period);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("run -> error: {}, retrying in {}ms...", e.getMessage(), RETRY_DELAY);
                sleep(RETRY_DELAY);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Map<UUID, String> findContainers() throws IOException, InterruptedException {
        Map<UUID, String> ids = new HashMap<>();
        Utils.exec(PS_CMD, line -> {
            int idx = line.indexOf(" ");
            if (idx < 0 || idx + 1 >= line.length()) {
                log.warn("findContainers -> invalid line: {}", line);
                return;
            }

            UUID k = UUID.fromString(line.substring(0, idx));
            String v = line.substring(idx + 1, line.length());

            ids.put(k, v);
        });
        return ids;
    }

    private static void killContainer(String cId) throws IOException, InterruptedException {
        Process b = new ProcessBuilder()
                .command(createKillCommand(cId))
                .start();

        int code = b.waitFor();
        if (code != 0) {
            throw new IOException("Error while removing a container " + cId + ": docker exit code " + code);
        }

        log.info("killContainer -> done, {} removed", cId);
    }

    private static String[] createKillCommand(String cId) {
        return new String[]{"docker", "rm", "-f", cId};
    }
}
