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

import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.LogUtils;
import com.walmartlabs.concord.server.queueclient.QueueClient;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.walmartlabs.concord.client.ClientUtils.withRetry;

public class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);

    private static final long ERROR_DELAY = 5000;
    private static final int PAYLOAD_DOWNLOAD_MAX_RETRIES = 3;
    private static final int PAYLOAD_DOWNLOAD_RETRY_DELAY = 3000;

    private final QueueClient queueClient;
    private final ProcessApiClient processApiClient;
    private final ExecutionManager executionManager;
    private final RepositoryManager repositoryManager;
    private final Path payloadDir;
    private final long logSteamMaxDelay;
    private final long pollInterval;
    private final Map<String, Object> capabilities;

    private volatile boolean maintenanceMode = false;

    public Worker(QueueClient queueClient,
                  ProcessApiClient processApiClient,
                  ExecutionManager executionManager,
                  RepositoryManager repositoryManager,
                  Path payloadDir,
                  long logSteamMaxDelay,
                  long pollInterval,
                  Map<String, Object> capabilities) {

        this.queueClient = queueClient;
        this.processApiClient = processApiClient;

        this.executionManager = executionManager;
        this.repositoryManager = repositoryManager;
        this.payloadDir = payloadDir;
        this.logSteamMaxDelay = logSteamMaxDelay;
        this.pollInterval = pollInterval;
        this.capabilities = capabilities;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted() && !maintenanceMode) {
            JobEntry job = null;
            try {

                job = take();

                if (job != null) {
                    execute(job.getInstanceId(), job.getPayload());
                }
            } catch (Exception e) {
                log.error("run -> job process error: {}", e.getMessage(), e);
                sleep(ERROR_DELAY);
            } finally {
                if (job != null) {
                    cleanup(job);
                }
            }

            if (job == null) {
                sleep(pollInterval);
            }
        }

        log.info("run -> done, maintenance mode: {}", maintenanceMode);
    }

    public void setMaintenanceMode() {
        maintenanceMode = true;
    }

    private JobEntry take() throws Exception {
        // TODO must be moved into the dispatcher thread
        Future<ProcessResponse> p = queueClient.request(new ProcessRequest(capabilities));

        ProcessResponse response = p.get();
        if (response == null) {
            return null;
        }

        UUID instanceId = response.getProcessId();
        Path workDir = null;
        try {
            workDir = IOUtils.createTempDir(payloadDir, "workDir");

            if (response.getRepoUrl() != null && response.getCommitId() != null) {
                repositoryManager.export(response.getOrgName(), response.getRepoUrl(), response.getCommitId(), response.getRepoPath(), response.getSecretName(), workDir);
            }

            downloadState(instanceId, workDir);
        } catch (Exception e) {
            delete(workDir);

            logError(instanceId, "Error while processing payload", e);
            handleError(instanceId, e);
            return null;
        }

        return new JobEntry(instanceId, workDir);
    }

    private void downloadState(UUID instanceId, Path workDir) throws Exception {
        File payload = null;
        try {
            payload = withRetry(PAYLOAD_DOWNLOAD_MAX_RETRIES, PAYLOAD_DOWNLOAD_RETRY_DELAY,
                    () -> processApiClient.downloadState(instanceId));

            IOUtils.unzip(payload.toPath(), workDir, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            if (payload != null) {
                delete(payload.toPath());
            }
        }
    }

    private void cleanup(JobEntry job) {
        try {
            Files.deleteIfExists(job.getPayload());
        } catch (IOException e) {
            log.warn("cleanup ['{}'] -> error while removing the payload {}: {}",
                    job.getInstanceId(), job.getPayload(), e.getMessage());
        }
    }

    private void cleanup(JobInstance i) {
        Path p = i.workDir();
        if (p == null || Files.notExists(p)) {
            return;
        }

        try {
            IOUtils.deleteRecursively(p);
        } catch (IOException e) {
            log(i.instanceId(), "Unable to delete the working directory: " + e.getMessage());
        }
    }

    private void delete(Path dir) {
        if (dir == null) {
            return;
        }

        try {
            IOUtils.deleteRecursively(dir);
        } catch (Exception e) {
            log.warn("delete ['{}'] -> error", dir, e);
        }
    }

    private void log(UUID instanceId, String s) {
        try {
            processApiClient.appendLog(instanceId, s.getBytes());
        } catch (ApiException e) {
            log.warn("log ['{}'] -> unable to append a log entry ({}): {}", instanceId, e.getMessage(), s);
        }
    }

    private void logError(UUID instanceId, String log, Object... args) {
        log(instanceId, LogUtils.formatMessage(LogUtils.LogLevel.ERROR, log, args));
    }

    private void execute(UUID instanceId, Path payload) {
        log.info("execute ['{}', '{}'] -> starting", instanceId, payload);

        JobInstance i;
        try {
            i = executionManager.start(instanceId, "n/a", payload);
        } catch (Exception e) {
            log.error("execute ['{}', '{}'] -> start error", instanceId, payload, e);
            return;
        }

        Consumer<Chunk> sink = chunk -> {
            byte[] ab = new byte[chunk.len];
            System.arraycopy(chunk.ab, 0, ab, 0, chunk.len);

            try {
                processApiClient.appendLog(instanceId, ab);
            } catch (ApiException e) {
                handleError(instanceId, e);
            }
        };

        Supplier<Boolean> isRunning = () -> !i.future().isDone();
        try {
            streamLog(i.logFile(), isRunning, sink);
        } catch (IOException e) {
            handleError(instanceId, e);
        }

        try {
            i.future().join();
            handleSuccess(instanceId);
        } catch (CancellationException | CompletionException e) {
            handleError(instanceId, e);
        } finally {
            cleanup(i);
        }

        log.info("execute ['{}', '{}'] -> done", instanceId, payload);
    }

    private void handleSuccess(UUID instanceId) {
        try {
            processApiClient.updateStatus(instanceId, ProcessEntry.StatusEnum.FINISHED);
        } catch (ApiException e) {
            log.warn("handleSuccess ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }
        log.info("handleSuccess ['{}'] -> done", instanceId);
    }

    private void handleError(UUID instanceId, Throwable error) {
        ProcessEntry.StatusEnum status = ProcessEntry.StatusEnum.FAILED;

        if (error instanceof CancellationException) {
            log.info("handleError ['{}'] -> job cancelled", instanceId);
            status = ProcessEntry.StatusEnum.CANCELLED;
        } else {
            log.error("handleError ['{}'] -> job failed", instanceId, error);
        }

        try {
            processApiClient.updateStatus(instanceId, status);
        } catch (ApiException e) {
            log.warn("handleError ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }

        log.info("handleError ['{}'] -> done", instanceId);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void streamLog(Path p, Supplier<Boolean> isRunning, Consumer<Chunk> sink) throws IOException {
        byte[] ab = new byte[8192];

        try (InputStream in = Files.newInputStream(p, StandardOpenOption.READ)) {
            while (true) {
                int read = in.read(ab, 0, ab.length);
                if (read > 0) {
                    sink.accept(new Chunk(ab, read));
                }

                if (read < ab.length) {
                    if (!isRunning.get()) {
                        // the log and the job are finished
                        break;
                    }

                    // job is still running, wait for more data
                    try {
                        Thread.sleep(logSteamMaxDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private static final class Chunk {

        private final byte[] ab;
        private final int len;

        private Chunk(byte[] ab, int len) { // NOSONAR
            this.ab = ab;
            this.len = len;
        }
    }

    private static class JobEntry {

        private final UUID instanceId;
        private final Path payload;

        public JobEntry(UUID instanceId, Path payload) {
            this.instanceId = instanceId;
            this.payload = payload;
        }

        public UUID getInstanceId() {
            return instanceId;
        }

        public Path getPayload() {
            return payload;
        }

        @Override
        public String toString() {
            return "JobEntry{" +
                    "instanceId='" + instanceId + '\'' +
                    ", payload=" + payload +
                    '}';
        }
    }
}
