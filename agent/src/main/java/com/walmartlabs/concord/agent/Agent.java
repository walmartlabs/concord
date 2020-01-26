package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.agent.Worker.CompletionCallback;
import com.walmartlabs.concord.agent.cfg.AgentConfiguration;
import com.walmartlabs.concord.agent.cfg.DockerConfiguration;
import com.walmartlabs.concord.agent.docker.OrphanSweeper;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.mmode.MaintenanceModeListener;
import com.walmartlabs.concord.agent.mmode.MaintenanceModeNotifier;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Named
@Singleton
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private final AgentConfiguration agentCfg;
    private final DockerConfiguration dockerCfg;

    private final QueueClient queueClient;
    private final ProcessLogFactory processLogFactory;
    private final StateFetcher stateFetcher;
    private final ProcessApi processApi;
    private final WorkerFactory workerFactory;

    private final Map<UUID, Worker> activeWorkers = new ConcurrentHashMap<>();
    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    @Inject
    public Agent(AgentConfiguration agentCfg,
                 DockerConfiguration dockerCfg,
                 QueueClient queueClient,
                 ProcessLogFactory processLogFactory,
                 StateFetcher stateFetcher,
                 ProcessApi processApi,
                 WorkerFactory workerFactory) {

        this.agentCfg = agentCfg;
        this.dockerCfg = dockerCfg;
        this.queueClient = queueClient;

        this.processLogFactory = processLogFactory;
        this.stateFetcher = stateFetcher;
        this.processApi = processApi;
        this.workerFactory = workerFactory;
    }

    public void run() throws Exception {
        ExecutorService executor = Executors.newCachedThreadPool();

        int workersCount = agentCfg.getWorkersCount();
        log.info("run -> using {} worker(s)", workersCount);
        Semaphore workersAvailable = new Semaphore(workersCount);

        // listen for maintenance mode requests
        startMaintenanceModeNotifier(queueClient);

        if (dockerCfg.isOrphanSweeperEnabled()) {
            executor.submit(new OrphanSweeper(this::isAlive, dockerCfg.getOrphanSweeperPeriod()));
        }

        // start the command handler in a separate thread
        CommandHandler commandHandler = new CommandHandler(agentCfg.getAgentId(), queueClient, agentCfg.getPollInterval(), this::cancel);
        executor.submit(commandHandler);

        // main loop
        while (!Thread.currentThread().isInterrupted()) {
            // check if the maintenance mode is enabled. If so, hang there indefinitely
            validateMaintenanceMode();

            // TODO parallel acquire?
            // wait for a free "slot"
            workersAvailable.acquire();
            log.info("run -> acquired a slot, {}/{} remains", workersAvailable.availablePermits(), workersCount);

            // fetch the next job
            JobRequest jobRequest;
            try {
                jobRequest = take(queueClient);
            } catch (Exception e) {
                log.warn("run -> error while fetching a job: {}", e.getMessage());

                workersAvailable.release();

                // wait before retrying
                // the server is not reachable or unhealthy, no point retrying immediately
                Utils.sleep(AgentConstants.ERROR_DELAY);
                continue;
            }

            if (jobRequest == null) {
                // can happen on switching to maintenance mode or reconnecting, etc
                workersAvailable.release();
                continue;
            }

            UUID instanceId = jobRequest.getInstanceId();

            // worker will handle the process' lifecycle
            Worker w = workerFactory.create(jobRequest, createStatusCallback(instanceId, workersAvailable));

            // register the worker so we can cancel it later
            activeWorkers.put(instanceId, w);

            // start a new thread to process the job
            executor.submit(w);
        }
    }

    private void startMaintenanceModeNotifier(QueueClient queueClient) {
        try {
            MaintenanceModeNotifier n = new MaintenanceModeNotifier(agentCfg.getMaintenanceModeListenerPort(), new MaintenanceModeListener() {
                @Override
                public Status onMaintenanceMode() {
                    maintenanceMode.set(true);
                    queueClient.maintenanceMode();
                    return getMaintenanceModeStatus();
                }

                @Override
                public Status getMaintenanceModeStatus() {
                    long cnt = activeWorkers.size();
                    return new Status(maintenanceMode.get(), cnt);
                }
            });
            n.start();
        } catch (IOException e) {
            log.warn("start -> can't start the maintenance mode notifier: {}", e.getMessage());
        }
    }

    private void validateMaintenanceMode() throws InterruptedException {
        while (maintenanceMode.get()) {
            log.info("run -> switched to maintenance mode");
            synchronized (maintenanceMode) {
                maintenanceMode.wait();
            }
            // TODO option to switch mmode off?
        }
    }

    private boolean isAlive(UUID instanceId) {
        return activeWorkers.containsKey(instanceId);
    }

    private CompletionCallback createStatusCallback(UUID instanceId, Semaphore workersAvailable) {
        return new CompletionCallback() {

            // guard agains misuse: the callback must be called only once - when the process reaches its
            // final status (successful or not)
            private volatile boolean called = false;

            @Override
            public void onStatusChange(StatusEnum status) {
                if (called) {
                    throw new IllegalStateException("The completion callback already called once");
                }
                called = true;

                activeWorkers.remove(instanceId);
                workersAvailable.release();

                log.info("onStatusChange -> {}: {}", instanceId, status);
                updateStatus(instanceId, status);
            }
        };
    }

    private JobRequest take(QueueClient queueClient) throws Exception {
        Future<ProcessResponse> req = queueClient.request(new ProcessRequest(agentCfg.getCapabilities()));

        ProcessResponse resp = req.get();
        if (resp == null) {
            return null;
        }

        Path workDir = IOUtils.createTempDir(agentCfg.getPayloadDir(), "workDir");

        return JobRequest.from(resp, workDir, processLogFactory);
    }

    private void updateStatus(UUID instanceId, StatusEnum s) {
        try {
            ClientUtils.withRetry(AgentConstants.API_CALL_MAX_RETRIES, AgentConstants.API_CALL_RETRY_DELAY, () -> {
                processApi.updateStatus(instanceId, agentCfg.getAgentId(), s.name());
                return null;
            });
        } catch (ApiException e) {
            log.warn("updateStatus ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }
    }

    private void cancel(UUID instanceId) {
        Worker w = activeWorkers.get(instanceId);
        if (w == null) {
            return;
        }

        w.cancel();
    }
}
