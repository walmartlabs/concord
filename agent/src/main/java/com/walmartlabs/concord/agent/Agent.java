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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.agent.Worker.CompletionCallback;
import com.walmartlabs.concord.agent.Worker.StateFetcher;
import com.walmartlabs.concord.agent.docker.OrphanSweeper;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.executors.runner.*;
import com.walmartlabs.concord.agent.executors.runner.DockerRunnerJobExecutor.DockerRunnerJobExecutorConfiguration;
import com.walmartlabs.concord.agent.executors.runner.RunnerJobExecutor.RunnerJobExecutorConfiguration;
import com.walmartlabs.concord.agent.logging.LogAppender;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.postprocessing.JobFileUploadPostProcessor;
import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessApi;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.client.SecretClient;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.QueueClientConfiguration;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);

    private static final long ERROR_DELAY = 5000;
    private static final int API_CALL_MAX_RETRIES = 3;
    private static final long API_CALL_RETRY_DELAY = 3000;

    private final Configuration cfg;

    private final ProcessApi processApi;
    private final ProcessLogFactory processLogFactory;
    private final ExecutorService executor;
    private final WorkerFactory workerFactory;

    private final Map<UUID, Worker> activeWorkers = new ConcurrentHashMap<>();
    private final AtomicBoolean maintenanceMode = new AtomicBoolean(false);

    public Agent(Configuration cfg) throws Exception {
        this.cfg = cfg;

        ApiClient apiClient = ApiClientFactory.create(cfg);
        this.processApi = new ProcessApi(apiClient);

        SecretClient secretClient = new SecretClient(apiClient);
        RepositoryManager repositoryManager = new RepositoryManager(cfg, secretClient);

        this.processLogFactory = new ProcessLogFactory(cfg.getLogDir(), cfg.getLogMaxDelay(), createLogAppender(processApi));

        this.executor = Executors.newCachedThreadPool();

        ProcessPool processPool = new ProcessPool(cfg.getMaxPreforkAge(), cfg.getMaxPreforkCount());
        DependencyManager dependencyManager = new DependencyManager(cfg.getDependencyCacheDir());
        ImportManagerProvider imp = new ImportManagerProvider(repositoryManager, dependencyManager);

        JobExecutor runnerExec = createRunnerJobExecutor(cfg, processApi, processLogFactory, processPool, dependencyManager, executor);
        Map<JobRequest.Type, JobExecutor> executors = Collections.singletonMap(JobRequest.Type.RUNNER, runnerExec);

        this.workerFactory = new WorkerFactory(repositoryManager, imp.get(), executors);
    }

    public void run() throws Exception {
        int workersCount = cfg.getWorkersCount();
        log.info("run -> using {} worker(s)", workersCount);
        Semaphore workersAvailable = new Semaphore(workersCount);

        // connect to the server's websocket
        QueueClient queueClient = connectToServer();

        // listen for maintenance mode requests
        startMaintenanceModeNotifier(queueClient);

        if (cfg.isDockerOrphanSweeperEnabled()) {
            executor.submit(new OrphanSweeper(this::isAlive, cfg.getDockerOrphanSweeperPeriod()));
        }

        // start the command handler in a separate thread
        CommandHandler commandHandler = new CommandHandler(cfg.getAgentId(), queueClient, cfg.getPollInterval(), this::cancel);
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
                Utils.sleep(ERROR_DELAY);
                continue;
            }

            if (jobRequest == null) {
                // can happen on switching to maintenance mode or reconnecting, etc
                workersAvailable.release();
                continue;
            }

            UUID instanceId = jobRequest.getInstanceId();

            // worker will handle the process' lifecycle
            Worker w = workerFactory.create(jobRequest, createStatusCallback(instanceId, workersAvailable), createStateFetcher());

            // register the worker so we can cancel it later
            activeWorkers.put(instanceId, w);

            // start a new thread to process the job
            executor.submit(w);
        }
    }

    private QueueClient connectToServer() throws URISyntaxException {
        QueueClient queueClient = new QueueClient(new QueueClientConfiguration.Builder(cfg.getServerWebsocketUrls())
                .apiKey(cfg.getApiKey())
                .userAgent(cfg.getUserAgent())
                .connectTimeout(cfg.getConnectTimeout())
                .pingInterval(cfg.getWebSocketPingInterval())
                .maxNoActivityPeriod(cfg.getWebsocketMaxNoActivityPeriod())
                .build());

        queueClient.start();

        return queueClient;
    }

    private void startMaintenanceModeNotifier(QueueClient queueClient) {
        try {
            MaintenanceModeNotifier n = new MaintenanceModeNotifier(new MaintenanceModeListener() {
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

    private StateFetcher createStateFetcher() {
        return new RemoteStateFetcher(processApi, API_CALL_MAX_RETRIES, API_CALL_RETRY_DELAY);
    }

    private JobRequest take(QueueClient queueClient) throws Exception {
        Future<ProcessResponse> req = queueClient.request(new ProcessRequest(cfg.getCapabilities()));

        ProcessResponse resp = req.get();
        if (resp == null) {
            return null;
        }

        Path workDir = IOUtils.createTempDir(cfg.getPayloadDir(), "workDir");

        return JobRequest.from(resp, workDir, processLogFactory);
    }

    private void updateStatus(UUID instanceId, StatusEnum s) {
        try {
            ClientUtils.withRetry(API_CALL_MAX_RETRIES, API_CALL_RETRY_DELAY, () -> {
                processApi.updateStatus(instanceId, cfg.getAgentId(), s.name());
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

    private LogAppender createLogAppender(ProcessApi processApi) {
        return (instanceId, ab) -> {
            String path = "/api/v1/process/" + instanceId + "/log";

            try {
                ClientUtils.withRetry(API_CALL_MAX_RETRIES, API_CALL_RETRY_DELAY, () -> {
                    ClientUtils.postData(processApi.getApiClient(), path, ab);
                    return null;
                });
            } catch (ApiException e) {
                // TODO handle errors
                log.warn("appendLog ['{}'] -> error: {}", instanceId, e.getMessage());
            }
        };
    }

    private static List<JobPostProcessor> createPostProcessors(ProcessApi processApi) {
        return Collections.singletonList(
                new JobFileUploadPostProcessor(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                        "attachments", (instanceId, data) -> {
                    String path = "/api/v1/process/" + instanceId + "/attachment";

                    ClientUtils.withRetry(API_CALL_MAX_RETRIES, API_CALL_RETRY_DELAY, () -> {
                        ClientUtils.postData(processApi.getApiClient(), path, data.toFile());
                        return null;
                    });
                })
        );
    }

    private static JobExecutor createRunnerJobExecutor(Configuration cfg,
                                                       ProcessApi processApi,
                                                       ProcessLogFactory processLogFactory,
                                                       ProcessPool processPool,
                                                       DependencyManager dependencyManager,
                                                       ExecutorService executor) {

        RunnerJobExecutorConfiguration runnerExecutorCfg = new RunnerJobExecutorConfiguration(cfg.getAgentId(),
                cfg.getServerApiBaseUrl(),
                cfg.getAgentJavaCmd(),
                cfg.getDependencyListsDir(),
                cfg.getRunnerPath(),
                cfg.getRunnerCfgDir(),
                cfg.isRunnerSecurityManagerEnabled(),
                cfg.getExtraDockerVolumes(),
                cfg.getMaxNoHeartbeatInterval());

        DefaultDependencies defaultDependencies = new DefaultDependencies();

        List<JobPostProcessor> postProcessors = createPostProcessors(processApi);

        return req -> {
            RunnerJobExecutor jobExecutor;
            RunnerJob job = RunnerJob.from(runnerExecutorCfg, req, processLogFactory);

            // TODO looks a bit messy, refactor to use proper configuration objects
            if (job.getProcessCfg().get(InternalConstants.Request.CONTAINER) != null) {
                DockerRunnerJobExecutorConfiguration dockerRunnerCfg = new DockerRunnerJobExecutorConfiguration(cfg.getDockerHost(), cfg.getDependencyCacheDir(), cfg.getJavaPath());
                jobExecutor = new DockerRunnerJobExecutor(runnerExecutorCfg, dockerRunnerCfg, dependencyManager, defaultDependencies, postProcessors, processPool, executor);
            } else {
                jobExecutor = new RunnerJobExecutor(runnerExecutorCfg, dependencyManager, defaultDependencies, postProcessors, processPool, executor);
            }
            return jobExecutor.exec(job);
        };
    }
}
