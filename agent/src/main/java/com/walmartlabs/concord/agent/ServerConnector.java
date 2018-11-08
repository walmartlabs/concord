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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.agent.docker.OrphanSweeper;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.QueueClientConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ServerConnector implements MaintenanceModeListener {

    private static final Logger log = LoggerFactory.getLogger(ServerConnector.class);

    private Worker[] workers;
    private Thread[] workerThreads;
    private Thread executionCleanup;
    private CommandHandler commandHandler;
    private Thread commandHandlerThread;
    private Thread orphanSweeper;
    private MaintenanceModeNotifier maintenanceModeNotifier;
    private CountDownLatch doneSignal;
    private QueueClient queueClient;
    private volatile boolean isMaintenanceMode;

    public void start(Configuration cfg) throws Exception {
        ApiClient apiClient = ApiClientFactory.create(cfg);
        ProcessApi processApi = new ProcessApi(apiClient);

        ExecutionManager executionManager = new ExecutionManager(cfg, processApi);

        try {
            maintenanceModeNotifier = new MaintenanceModeNotifier(this);
            maintenanceModeNotifier.start();
        } catch (IOException e) {
            log.warn("start -> can't start the maintenance mode notifier: {}", e.getMessage());
        }

        executionCleanup = new Thread(new ExecutionStatusCleanup(executionManager),
                "execution-status-cleanup");
        executionCleanup.start();

        queueClient = new QueueClient(new QueueClientConfiguration.Builder(cfg.getServerWebsocketUrl())
                .apiToken(cfg.getApiKey())
                .userAgent(cfg.getUserAgent())
                .connectTimeout(cfg.getConnectTimeout())
                .maxInactivityPeriod(cfg.getMaxWebSocketInactivity())
                .build());
        queueClient.start();

        commandHandler = new CommandHandler(cfg.getAgentId(), queueClient, executionManager, Executors.newCachedThreadPool(), cfg.getPollInterval());
        commandHandlerThread = new Thread(commandHandler, "command-handler");
        commandHandlerThread.start();

        int workersCount = cfg.getWorkersCount();
        log.info("start -> using {} worker(s)", workersCount);

        doneSignal = new CountDownLatch(workersCount);

        workers = new Worker[workersCount];
        workerThreads = new Thread[workersCount];

        ProcessApiClient processApiClient = new ProcessApiClient(cfg, processApi);

        for (int i = 0; i < workersCount; i++) {
            workers[i] = new Worker(queueClient, processApiClient, executionManager,
                    cfg.getLogMaxDelay(), cfg.getPollInterval(), cfg.getCapabilities(), doneSignal);

            workerThreads[i] = new Thread(workers[i], "worker-" + i);
        }

        for (Thread w : workerThreads) {
            w.start();

            // offset the worker start up slightly to smooth out the polling intervals
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        if (cfg.isDockerOrphanSweeperEnabled()) {
            long t = cfg.getDockerOrphanSweeperPeriod();
            orphanSweeper = new Thread(new OrphanSweeper(executionManager, t), "docker-sweeper");
            orphanSweeper.start();
        }
    }

    public synchronized void stop() {
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
            maintenanceModeNotifier.stop();
            maintenanceModeNotifier = null;
        }

        if (queueClient != null) {
            queueClient.stop();
            queueClient = null;
        }
    }

    @Override
    public Status onMaintenanceMode() {
        isMaintenanceMode = true;

        if (workers != null) {
            Stream.of(workers).forEach(Worker::setMaintenanceMode);
        }

        queueClient.maintenanceMode();

        try {
            doneSignal.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new Status(true, doneSignal.getCount());
    }

    @Override
    public Status getMaintenanceModeStatus() {
        return new Status(isMaintenanceMode, doneSignal.getCount());
    }
}
