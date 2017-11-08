package com.walmartlabs.concord.agent;

import com.walmartlabs.concord.rpc.*;
import com.walmartlabs.concord.sdk.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Worker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(Worker.class);
    private static final long ERROR_DELAY = 5000;

    private final AgentApiClient client;
    private final ExecutionManager executionManager;
    private final long logSteamMaxDelay;

    public Worker(AgentApiClient client, ExecutionManager executionManager, long logSteamMaxDelay) {
        this.client = client;
        this.executionManager = executionManager;
        this.logSteamMaxDelay = logSteamMaxDelay;
    }

    @Override
    public void run() {
        JobQueue q = client.getJobQueue();

        while (!Thread.currentThread().isInterrupted()) {
            JobEntry job;
            try {
                job = q.take();
            } catch (ClientException e) {
                log.warn("run -> transport error: {}", e.getMessage());
                sleep(ERROR_DELAY);
                continue;
            }

            if (job == null) {
                continue;
            }

            execute(job.getInstanceId(), job.getJobType(), job.getPayload());
        }
    }

    private void execute(String instanceId, JobType type, Path payload) {
        log.info("execute ['{}', '{}', '{}'] -> starting", instanceId, type, payload);

        JobInstance i;
        try {
            i = executionManager.start(instanceId, type, "n/a", payload);
        } catch (ExecutionException e) {
            log.error("execute ['{}', '{}', '{}'] -> start error", instanceId, type, payload, e);
            // TODO handle error
            return;
        }

        JobQueue q = client.getJobQueue();

        try {
            q.update(instanceId, JobStatus.RUNNING);
        } catch (ClientException e) {
            log.error("execute ['{}', '{}', '{}'] -> status update error", instanceId, type, payload, e);
            // TODO handle error
        }

        Consumer<Chunk> sink = chunk -> {
            byte[] ab = new byte[chunk.len];
            System.arraycopy(chunk.ab, 0, ab, 0, chunk.len);

            try {
                q.appendLog(instanceId, ab);
            } catch (ClientException e) {
                handleError(instanceId, e);
            }
        };

        CompletableFuture<?> f = i.future();
        Supplier<Boolean> isRunning = () -> executionManager.getStatus(instanceId) == JobStatus.RUNNING;

        try {
            streamLog(i.logFile(), isRunning, sink);
        } catch (IOException e) {
            handleError(instanceId, e);
        }

        try {
            f.join();
            handleSuccess(instanceId);
        } catch (CancellationException | CompletionException e) {
            handleError(instanceId, e);
        }
    }

    private void handleSuccess(String instanceId) {
        JobQueue q = client.getJobQueue();
        try {
            q.update(instanceId, JobStatus.COMPLETED);
        } catch (ClientException e) {
            // TODO retries?
            log.warn("handleSuccess ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }
    }

    private void handleError(String instanceId, Throwable error) {
        JobStatus status = JobStatus.FAILED;

        if (error instanceof CancellationException) {
            log.info("handleError ['{}'] -> job cancelled", instanceId);
            status = JobStatus.CANCELLED;
        } else {
            log.error("handleError ['{}'] -> job failed", instanceId, error);
        }

        JobQueue q = client.getJobQueue();
        try {
            q.update(instanceId, status);
        } catch (ClientException e) {
            // TODO retries?
            log.warn("handleError ['{}'] -> error while updating status of a job: {}", instanceId, e.getMessage());
        }
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

        private Chunk(byte[] ab, int len) {
            this.ab = ab;
            this.len = len;
        }
    }
}
