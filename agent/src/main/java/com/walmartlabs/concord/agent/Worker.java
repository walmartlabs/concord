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
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

public class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private final RepositoryManager repositoryManager;
    private final JobExecutor executor;
    private final StatusCallback statusCallback;
    private final StateFetcher stateFetcher;
    private final JobRequest jobRequest;

    private JobInstance jobInstance;

    public Worker(RepositoryManager repositoryManager,
                  JobExecutor executor,
                  StatusCallback statusCallback,
                  StateFetcher stateFetcher,
                  JobRequest jobRequest) {

        this.repositoryManager = repositoryManager;
        this.executor = executor;
        this.jobRequest = jobRequest;
        this.statusCallback = statusCallback;
        this.stateFetcher = stateFetcher;
    }

    @Override
    public void run() {
        log.info("run -> starting {}", jobRequest);
        UUID instanceId = jobRequest.getInstanceId();

        try {
            // fetch the git repo's data...
            fetchRepo(jobRequest);
            // ...and download the saved process state from the server
            downloadState(jobRequest);

            jobRequest.getLog().info("Starting using {}...", executor);

            // execute the job
            jobInstance = executor.exec(jobRequest);
            jobInstance.waitForCompletion();

            // successful completion
            log.info("run -> done with {}", jobRequest);
            statusCallback.onStatusChange(StatusEnum.FINISHED);
        } catch (Exception e) {
            // unwrap the exception if needed
            Throwable t = unwrap(e);

            // handle any error during the startup or the execution
            handleError(instanceId, e);
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

    private void handleError(UUID instanceId, Throwable error) {
        StatusEnum status = StatusEnum.FAILED;

        if (jobInstance != null && jobInstance.isCancelled()) {
            log.info("handleError ['{}'] -> job cancelled", instanceId);
            status = StatusEnum.CANCELLED;
        } else {
            log.error("handleError ['{}'] -> job failed", instanceId, error);
        }

        statusCallback.onStatusChange(status);
        log.info("handleError ['{}'] -> done", instanceId);
    }

    private void fetchRepo(JobRequest r) throws Exception {
        if (r.getRepoUrl() == null || r.getCommitId() == null) {
            return;
        }

        long dt = withTimer(() -> repositoryManager.export(r.getOrgName(),
                r.getSecretName(),
                r.getRepoUrl(),
                r.getCommitId(),
                r.getRepoPath(),
                r.getPayloadDir()));

        r.getLog().info("Repository data export took {}ms", dt);
    }

    private void downloadState(JobRequest r) throws Exception {
        long dt = withTimer(() -> stateFetcher.downloadState(r));
        r.getLog().info("Process state download took {}ms", dt);
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

    public interface StateFetcher {

        void downloadState(JobRequest jobRequest) throws Exception;
    }

    public interface StatusCallback {

        void onStatusChange(StatusEnum status);
    }
}
