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
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.CommandQueueApi;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessQueueApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class ServerConnector implements MaintenanceModeListener {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private Worker[] workers;
    private Thread[] workerThreads;
    private Thread executionCleanup;
    private CommandHandler commandHandler;
    private Thread commandHandlerThread;

    private Thread orphanSweeper;
    private Thread oldImageSweeper;

    private Thread maintenanceModeNotifier;

    public void start(Configuration cfg) throws IOException {
        String host = cfg.getServerHost();
        int port = cfg.getServerRpcPort();
        int workersCount = cfg.getWorkersCount();

        log.info("start -> connecting to {}:{}", host, port);

        ExecutionManager executionManager = new ExecutionManager(cfg, new ProcessApi(createClient(cfg)));

        maintenanceModeNotifier = new Thread(new MaintenanceModeNotifier(cfg.getMaintenanceModeFile(), this),
                "maintenance-mode-notifier");
        maintenanceModeNotifier.start();

        executionCleanup = new Thread(new ExecutionStatusCleanup(executionManager),
                "execution-status-cleanup");
        executionCleanup.start();

        commandHandler = new CommandHandler(cfg.getAgentId(), new CommandQueueApi(createClient(cfg)), executionManager, Executors.newCachedThreadPool(), cfg.getPollInterval());
        commandHandlerThread = new Thread(commandHandler, "command-handler");
        commandHandlerThread.start();

        workers = new Worker[workersCount];
        workerThreads = new Thread[workersCount];

        for (int i = 0; i < workersCount; i++) {
            Worker w = new Worker(new ProcessQueueApi(createClient(cfg)),
                    new ProcessApiClient(cfg, new ProcessApi(createClient(cfg))),
                    executionManager, cfg.getLogMaxDelay(), cfg.getPollInterval(),
                    cfg.getCapabilities());
            workers[i] = w;
            Thread t = new Thread(w, "worker-" + i);
            workerThreads[i] = t;
        }

        for (Thread w : workerThreads) {
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

        if (commandHandlerThread != null) {
            commandHandlerThread.interrupt();
            commandHandler = null;
            commandHandlerThread = null;
        }

        if (workerThreads != null) {
            for (Thread w : workerThreads) {
                w.interrupt();
            }
            workerThreads = null;
            workers = null;
        }

        if (executionCleanup != null) {
            executionCleanup.interrupt();
            executionCleanup = null;
        }

        if (maintenanceModeNotifier != null) {
            maintenanceModeNotifier.interrupt();
            maintenanceModeNotifier = null;
        }
    }

    @Override
    public void onMaintenanceMode() {
        if (workers != null) {
            Stream.of(workers).forEach(Worker::setMaintenanceMode);
        }

        if (commandHandler != null) {
            commandHandler.setMaintenanceMode();
        }
    }

    private static ApiClient createClient(Configuration cfg) throws IOException {
        ApiClient client = new ApiClient();
        client.setTempFolderPath(IOUtils.createTempDir("agent-client").toString());
        client.setBasePath(cfg.getServerApiBaseUrl());
        client.setApiKey(cfg.getApiKey());
        client.setReadTimeout(cfg.getReadTimeout());
        client.setConnectTimeout(cfg.getConnectTimeout());
        client.setUserAgent("Concord-Agent: id=" + cfg.getAgentId());
        return client;
    }
}
