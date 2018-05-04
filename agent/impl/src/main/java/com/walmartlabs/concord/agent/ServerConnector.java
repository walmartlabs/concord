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

import com.walmartlabs.concord.agent.docker.OldImageSweeper;
import com.walmartlabs.concord.agent.docker.OrphanSweeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;

public class ServerConnector {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private Thread[] workers;
    private Thread executionCleanup;
    private Thread commandHandler;

    private Thread orphanSweeper;
    private Thread oldImageSweeper;

    public void start(Configuration cfg) throws IOException {
        String agentId = cfg.getAgentId();
        String host = cfg.getServerHost();
        int port = cfg.getServerRpcPort();
        int workersCount = cfg.getWorkersCount();

        log.info("start -> connecting to {}:{}", host, port);

        ExecutionManager executionManager = new ExecutionManager(cfg);

        executionCleanup = new Thread(new ExecutionStatusCleanup(executionManager),
                "execution-status-cleanup");
        executionCleanup.start();

        commandHandler = new Thread(new CommandHandler(new CommandQueueClient(cfg), executionManager, Executors.newCachedThreadPool()),
                "command-handler");
        commandHandler.start();

        workers = new Thread[workersCount];
        for (int i = 0; i < workersCount; i++) {
            Worker w = new Worker(new ProcessApiClient(cfg), new JobQueueClient(cfg), executionManager, cfg.getLogMaxDelay());
            Thread t = new Thread(w, "worker-" + i);
            workers[i] = t;
        }

        for (Thread w : workers) {
            w.start();
        }

        if (cfg.isDockerOrphanSweeperEnabled()) {
            long t = cfg.getDockerOrphanSweeperPeriod();
            orphanSweeper = new Thread(new OrphanSweeper(executionManager, t),
                    "docker-sweeper");
            orphanSweeper.start();
        }

        if (cfg.isDockerOldImageSweeperEnabled()) {
            long t = cfg.getDockerOldImageSweeperPeriod();
            oldImageSweeper = new Thread(new OldImageSweeper(t));
            oldImageSweeper.start();
        }
    }

    public synchronized void stop() {
        if (oldImageSweeper != null) {
            oldImageSweeper.interrupt();
            oldImageSweeper = null;
        }

        if (orphanSweeper != null) {
            orphanSweeper.interrupt();
            orphanSweeper = null;
        }

        if (commandHandler != null) {
            commandHandler.interrupt();
            commandHandler = null;
        }

        if (workers != null) {
            for (Thread w : workers) {
                w.interrupt();
            }
            workers = null;
        }

        if (executionCleanup != null) {
            executionCleanup.interrupt();
            executionCleanup = null;
        }
    }
}
