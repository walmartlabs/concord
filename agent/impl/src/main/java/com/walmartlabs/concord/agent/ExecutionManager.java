package com.walmartlabs.concord.agent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.common.DependencyManager;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.rpc.AgentApiClient;
import com.walmartlabs.concord.rpc.JobStatus;
import com.walmartlabs.concord.rpc.JobType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long JOB_ENTRY_TTL = 8 * 60 * 60 * 1000; // 8 hours

    private final Map<JobType, JobExecutor> jobExecutors;
    private final LogManager logManager;
    private final Configuration cfg;

    private final Cache<String, JobStatus> statuses = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Cache<String, JobInstance> instances = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Object mutex = new Object();

    public ExecutionManager(AgentApiClient client, Configuration cfg) throws IOException {

        this.logManager = new LogManager(cfg);
        this.cfg = cfg;
        this.jobExecutors = new HashMap<>();

        DependencyManager dependencyManager = new DependencyManager(cfg.getDependencyCacheDir());
        ExecutorService executorService = Executors.newCachedThreadPool();

        jobExecutors.put(JobType.JAR, new JarJobExecutor(cfg, logManager, dependencyManager, executorService));
        jobExecutors.put(JobType.RUNNER, new RunnerJobExecutor(cfg, logManager, dependencyManager, executorService, client));
    }

    public JobInstance start(String instanceId, JobType type, String entryPoint, InputStream payload) throws ExecutionException {
        JobExecutor exec = jobExecutors.get(type);
        if (exec == null) {
            throw new IllegalArgumentException("Unknown job type: " + type);
        }

        // remove the previous log file
        // e.g. left after the execution was suspended
        logManager.delete(instanceId);
        logManager.touch(instanceId);

        Path tmpDir = extract(payload);

        synchronized (mutex) {
            statuses.put(instanceId, JobStatus.RUNNING);
        }

        JobInstance i;
        try {
            i = exec.start(instanceId, tmpDir, entryPoint);
        } catch (Exception e) {
            log.warn("start ['{}', {}, '{}'] -> failed", instanceId, type, entryPoint, e);
            handleError(instanceId);
            throw e;
        }

        synchronized (mutex) {
            instances.put(instanceId, i);
        }

        i.future().thenRun(() -> {
            synchronized (mutex) {
                statuses.put(instanceId, JobStatus.COMPLETED);
            }
        }).exceptionally(e -> {
            handleError(instanceId);
            return null;
        });

        return i;
    }

    private void handleError(String instanceId) {
        synchronized (mutex) {
            JobStatus s = statuses.getIfPresent(instanceId);
            if (s != JobStatus.CANCELLED) {
                statuses.put(instanceId, JobStatus.FAILED);
            }
        }
    }

    public void cancel(String id) {
        synchronized (mutex) {
            JobStatus s = statuses.getIfPresent(id);
            if (s != null && s == JobStatus.RUNNING) {
                statuses.put(id, JobStatus.CANCELLED);
            }
        }

        JobInstance i = instances.getIfPresent(id);
        if (i == null) {
            return;
        }

        i.cancel();
    }

    public JobStatus getStatus(String id) {
        JobStatus s;
        synchronized (mutex) {
            s = statuses.getIfPresent(id);
        }

        if (s == null) {
            throw new IllegalArgumentException("Unknown execution ID: " + id);
        }

        return s;
    }

    private Path extract(InputStream in) throws ExecutionException {
        Path baseDir = cfg.getPayloadDir();
        try {
            Path dst = Files.createTempDirectory(baseDir, "workDir");
            Files.createDirectories(dst);

            try (ZipInputStream zip = new ZipInputStream(in)) {
                IOUtils.unzip(zip, dst);
                return dst;
            }
        } catch (IOException e) {
            throw new ExecutionException("Error while unpacking a payload", e);
        }
    }
}
