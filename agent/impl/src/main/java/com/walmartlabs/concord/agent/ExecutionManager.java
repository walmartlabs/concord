package com.walmartlabs.concord.agent;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.agent.Client;
import com.walmartlabs.concord.server.api.agent.JobStatus;
import com.walmartlabs.concord.server.api.agent.JobType;
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
import java.util.zip.ZipOutputStream;

//@Named
//@Singleton
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

    //    @Inject
    public ExecutionManager(/*JarJobExecutor jarJobExecutor,
                            RunnerJobExecutor runnerJobExecutor,
                            LogManager logManager,*/
                            Client client,
                            Configuration cfg) {

        this.logManager = new LogManager(cfg);
        this.cfg = cfg;
        this.jobExecutors = new HashMap<>();

        DependencyManager dependencyManager = new DependencyManager(cfg);
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

        Path tmpDir = extract(instanceId, payload);

        synchronized (mutex) {
            statuses.put(instanceId, JobStatus.RUNNING);
        }

//        JobInstance i = exec.start(instanceId, tmpDir, entryPoint);
        JobInstance i;
        try {
            i = exec.start(instanceId, tmpDir, entryPoint);
        } catch (Exception e) {
            log.error("start ['{}', {}, '{}'] -> failed", instanceId, type, entryPoint, e);
            handleError(instanceId, e);
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
            handleError(instanceId, e);
            return null;
        });

        return i;
    }

    private void handleError(String instanceId, Throwable e) {
        synchronized (mutex) {
            JobStatus s = statuses.getIfPresent(instanceId);
            if (s != JobStatus.CANCELLED) {
                statuses.put(instanceId, JobStatus.FAILED);
            }
        }
    }

    public int countRunning() {
        /*
        int i = 0;
        synchronized (mutex) {
            for (Map.Entry<String, JobStatus> e : statuses.asMap().entrySet()) {
                if (e.getValue() == JobStatus.RUNNING) {
                    i++;
                }
            }
        }
        return i;
        */
        return 0;
    }

    public void cancel() {
        /*
        Collection<String> ids;
        synchronized (mutex) {
            ids = new ArrayList<>(instances.asMap().keySet());
        }

        for (String id : ids) {
            cancel(id);
        }
        */
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

    public Path zipAttachments(String id) throws IOException {
        Path src = getAttachmentDir(id);
        if (src == null || !Files.exists(src)) {
            return null;
        }

        Path tmpFile = Files.createTempFile("attachments", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmpFile))) {
            IOUtils.zip(zip, src);
        }

        return tmpFile;
    }

    public void removeAttachments(String id) throws IOException {
        Path p = getAttachmentDir(id);
        if (p == null || !Files.exists(p)) {
            return;
        }

        IOUtils.deleteRecursively(p);
    }

    private Path getAttachmentDir(String instanceId) {
        /*
        synchronized (mutex) {
            JobInstance i = instances.getIfPresent(instanceId);
            if (i == null) {
                return null;
            }
            return i.getWorkDir().resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        }*/
        return null;
    }

    private Path extract(String id, InputStream in) throws ExecutionException {
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
