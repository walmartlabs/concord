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
import com.walmartlabs.concord.agent.docker.OldImageSweeper;
import com.walmartlabs.concord.agent.docker.OrphanSweeper;
import com.walmartlabs.concord.client.CommandQueueApi;
import com.walmartlabs.concord.client.ConcordApiClient;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessQueueApi;
import com.walmartlabs.concord.common.IOUtils;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class ServerConnector implements MaintenanceModeListener {

    private Worker[] workers;
    private Thread[] workerThreads;
    private Thread executionCleanup;
    private CommandHandler commandHandler;
    private Thread commandHandlerThread;

    private Thread orphanSweeper;
    private Thread oldImageSweeper;

    private MaintenanceModeNotifier maintenanceModeNotifier;

    private CountDownLatch doneSignal;

    public void start(Configuration cfg) throws IOException {
        int workersCount = cfg.getWorkersCount();

        ApiClient apiClient = createClient(cfg);
        ProcessApi processApi = new ProcessApi(apiClient);

        ExecutionManager executionManager = new ExecutionManager(cfg, processApi);

        maintenanceModeNotifier = new MaintenanceModeNotifier(this);
        maintenanceModeNotifier.start();

        executionCleanup = new Thread(new ExecutionStatusCleanup(executionManager),
                "execution-status-cleanup");
        executionCleanup.start();

        commandHandler = new CommandHandler(cfg.getAgentId(), new CommandQueueApi(apiClient), executionManager, Executors.newCachedThreadPool(), cfg.getPollInterval());
        commandHandlerThread = new Thread(commandHandler, "command-handler");
        commandHandlerThread.start();

        doneSignal = new CountDownLatch(workersCount);

        workers = new Worker[workersCount];
        workerThreads = new Thread[workersCount];

        ProcessQueueApi queueApi = new ProcessQueueApi(apiClient);
        ProcessApiClient processApiClient = new ProcessApiClient(cfg, processApi);

        for (int i = 0; i < workersCount; i++) {
            workers[i] = new Worker(queueApi, processApiClient, executionManager,
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
            maintenanceModeNotifier.stop();
            maintenanceModeNotifier = null;
        }
    }

    @Override
    public long onMaintenanceMode() {
        if (workers != null) {
            Stream.of(workers).forEach(Worker::setMaintenanceMode);
        }

        try {
            doneSignal.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return doneSignal.getCount();
    }

    private static ApiClient createClient(Configuration cfg) throws IOException {
        ApiClient client = new ConcordApiClient(cfg.getServerApiBaseUrl());
        client.setTempFolderPath(IOUtils.createTempDir("agent-client").toString());
        client.setApiKey(cfg.getApiKey());
        client.setReadTimeout(cfg.getReadTimeout());
        client.setConnectTimeout(cfg.getConnectTimeout());
        client.setUserAgent(cfg.getUserAgent());
        return client;
    }
}
