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

import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.guice.AgentImportManager;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.remote.ProcessStatusUpdater;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.imports.Import.SecretDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;

public class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final RepositoryManager repositoryManager;
    private final AgentImportManager importManager;
    private final JobExecutor executor;
    private final CompletionCallback completionCallback;
    private final StateFetcher stateFetcher;
    private final ProcessStatusUpdater processStatusUpdater;
    private final ProcessLog processLog;
    private final JobRequest jobRequest;

    private JobInstance jobInstance;
    private boolean throwOnFailure;

    @Inject
    public Worker(RepositoryManager repositoryManager,
                  AgentImportManager importManager,
                  JobExecutor executor,
                  CompletionCallback completionCallback,
                  StateFetcher stateFetcher,
                  ProcessStatusUpdater processStatusUpdater,
                  ProcessLog processLog,
                  JobRequest jobRequest) {

        this.repositoryManager = repositoryManager;
        this.importManager = importManager;
        this.executor = executor;
        this.completionCallback = completionCallback;
        this.stateFetcher = stateFetcher;
        this.processStatusUpdater = processStatusUpdater;
        this.processLog = processLog;
        this.jobRequest = jobRequest;
    }

    @Override
    public void run() {
        log.info("run -> starting {}", jobRequest);
        UUID instanceId = jobRequest.getInstanceId();

        try {
            // fetch the git repo's data...
            fetchRepo(jobRequest);
            // ...and process imports
            processImports(jobRequest);
            // ...and download the saved process state from the server
            downloadState(jobRequest);

            // load the process' configuration
            ConfiguredJobRequest configuredJobRequest = ConfiguredJobRequest.from(jobRequest);

            // execute the job
            jobInstance = executor.exec(configuredJobRequest);
            jobInstance.waitForCompletion();

            // successful completion
            log.info("run -> done with {}", configuredJobRequest);
            onStatusChange(instanceId, StatusEnum.FINISHED);
        } catch (Throwable e) {
            // unwrap the exception if needed
            Throwable t = unwrap(e);

            // handle any error during the startup or the execution
            handleError(instanceId, t);
        } finally {
            Path payloadDir = jobRequest.getPayloadDir();
            try {
                log.info("exec ['{}'] -> removing the payload directory: {}", instanceId, payloadDir);
                IOUtils.deleteRecursively(payloadDir);
            } catch (IOException e) {
                log.warn("exec ['{}'] -> can't remove the payload directory: {}", instanceId, e.getMessage());
            }
        }
    }

    public void cancel() {
        if (jobInstance == null) {
            return;
        }

        jobInstance.cancel();
    }

    public void setThrowOnFailure(boolean throwOnFailure) {
        this.throwOnFailure = throwOnFailure;
    }

    private void handleError(UUID instanceId, Throwable error) {
        StatusEnum status = StatusEnum.FAILED;

        if (jobInstance != null && jobInstance.isCancelled()) {
            log.info("handleError ['{}'] -> job cancelled", instanceId);
            status = StatusEnum.CANCELLED;
        } else {
            log.error("handleError ['{}'] -> job failed", instanceId, error);
        }

        onStatusChange(instanceId, status);
        log.info("handleError ['{}'] -> done", instanceId);

        if (throwOnFailure) {
            if (error instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(error);
        }
    }

    private void onStatusChange(UUID instanceId, StatusEnum status) {
        try {
            processStatusUpdater.update(instanceId, status);
        } finally {
            completionCallback.onStatusChange(status);
        }
    }

    private void fetchRepo(JobRequest r) throws Exception {
        if (r.getRepoUrl() == null
                || (r.getCommitId() == null && r.getRepoBranch() == null)
                || r.getRepoUrl().startsWith("classpath://")) {
            return;
        }

        processLog.info("Exporting the repository data: {} @ {}:{}, path: {}",
                r.getRepoUrl(), r.getRepoBranch() != null ? r.getRepoBranch() : "*",
                r.getCommitId(), r.getRepoPath() != null ? r.getRepoPath() : "/");

        long dt;
        try {
            dt = withTimer(() -> repositoryManager.export(
                    r.getRepoUrl(),
                    r.getRepoBranch(),
                    r.getCommitId(),
                    r.getRepoPath(),
                    r.getPayloadDir(),
                    getSecret(r),
                    Collections.emptyList()));
        } catch (Exception e) {
            processLog.error("Repository export error: {}", e.getMessage(), e);
            throw e;
        }

        processLog.info("Repository data export took {}ms", dt);
    }

    private static SecretDefinition getSecret(JobRequest r) {
        if (r.getSecretName() == null) {
            return null;
        }

        return SecretDefinition.builder()
                .org(r.getOrgName())
                .name(r.getSecretName())
                .build();
    }

    private void downloadState(JobRequest r) throws Exception {
        processLog.info("Downloading the process state...");

        long dt;
        try {
            dt = withTimer(() -> stateFetcher.downloadState(r));
        } catch (Exception e) {
            processLog.error("State download error: {}", e.getMessage());
            throw e;
        }

        processLog.info("Process state download took {}ms", dt);
    }

    private void processImports(JobRequest r) throws ExecutionException {
        if (r.getImports().isEmpty()) {
            return;
        }

        long dt;
        try {
            dt = withTimer(() -> importManager.process(r.getImports(), r.getPayloadDir()));
        } catch (Exception e) {
            processLog.error("Error while reading the process' imports: " + e.getMessage());
            throw new ExecutionException("Error while reading the process' imports", e);
        }

        processLog.info("Import of external resources took {}ms", dt);
    }

    private static Throwable unwrap(Throwable t) {
        if (t instanceof ExecutionException && t.getCause() != null) {
            t = t.getCause();
        }

        if (t instanceof RuntimeException && t.getCause() != null) {
            t = t.getCause();
        }

        return t;
    }

    private static long withTimer(Fn f) throws Exception {
        long t1 = System.currentTimeMillis();
        f.apply();
        long t2 = System.currentTimeMillis();
        return t2 - t1;
    }

    private interface Fn {

        void apply() throws Exception;
    }

    public interface CompletionCallback {

        void onStatusChange(StatusEnum status);
    }
}
