package com.walmartlabs.concord.agent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.common.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipInputStream;

@Named
@Singleton
public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long STATUS_TTL = 8 * 60 * 60 * 1000; // 8 hours

    private final Map<JobType, JobExecutor> jobExecutors;
    private final ExecutorService executor;
    private final LogManager logManager;

    private final Map<String, Future<?>> executions = new HashMap<>();
    private final Cache<String, JobStatus> statuses = CacheBuilder.newBuilder()
            .expireAfterAccess(STATUS_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Object mutex = new Object();

    @Inject
    public ExecutionManager(@Named("executionPool") ExecutorService executor,
                            JarJobExecutor jarJobExecutor,
                            LogManager logManager) {

        this.executor = executor;

        this.logManager = logManager;
        this.jobExecutors = new HashMap<>();

        jobExecutors.put(JobType.JAR, jarJobExecutor);
        jobExecutors.put(JobType.JUNIT_GROOVY, new JunitGroovyJobExecutor());
    }

    public String start(InputStream payload, JobType type, String entryPoint) throws ExecutionException {
        JobExecutor exec = jobExecutors.get(type);
        if (exec == null) {
            throw new IllegalArgumentException("Unknown job type: " + type);
        }

        Path tmpDir = extract(payload);

        String id = UUID.randomUUID().toString();
        logManager.log(id, "Job type: %s", type);

        synchronized (mutex) {
            statuses.put(id, JobStatus.RUNNING);
        }

        Future<?> f = executor.submit(() -> {
            // TODO ugly
            try {
                exec.exec(id, tmpDir, entryPoint);
                synchronized (mutex) {
                    statuses.put(id, JobStatus.COMPLETED);
                }
            } catch (Exception e) {
                log.error("start ['{}', {}, '{}'] -> failed", id, type, entryPoint, e);
                synchronized (mutex) {
                    JobStatus s = statuses.getIfPresent(id);
                    if (s != JobStatus.CANCELLED) {
                        statuses.put(id, JobStatus.FAILED);
                    }
                }
            } finally {
                synchronized (ExecutionManager.this) {
                    executions.remove(id);
                }
            }
        });

        synchronized (mutex) {
            executions.put(id, f);
        }

        return id;
    }

    public void cancel() {
        Collection<String> ids;
        synchronized (mutex) {
            ids = new ArrayList<>(executions.keySet());
        }

        for (String id : ids) {
            cancel(id, false);
        }
    }

    public void cancel(String id, boolean waitToFinish) {
        synchronized (mutex) {
            JobStatus s = statuses.getIfPresent(id);
            if (s != null && s == JobStatus.RUNNING) {
                statuses.put(id, JobStatus.CANCELLED);
            }
        }

        Future<?> f = executions.get(id);
        if (f == null) {
            return;
        }
        f.cancel(true);

        if (waitToFinish) {
            while (!f.isDone()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
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

    public int jobCount() {
        synchronized (mutex) {
            return executions.size();
        }
    }

    private static Path extract(InputStream in) throws ExecutionException {
        try (ZipInputStream zip = new ZipInputStream(in)) {
            // TODO cfg?
            Path tmpDir = Files.createTempDirectory("runner");
            IOUtils.unzip(zip, tmpDir);
            return tmpDir;
        } catch (IOException e) {
            throw new ExecutionException("Error while unpacking a payload", e);
        }
    }
}
