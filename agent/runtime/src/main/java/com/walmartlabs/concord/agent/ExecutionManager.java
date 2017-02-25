package com.walmartlabs.concord.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.common.Constants;
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
import java.util.zip.ZipOutputStream;

@Named
@Singleton
public class ExecutionManager {

    private static final Logger log = LoggerFactory.getLogger(ExecutionManager.class);
    private static final long JOB_ENTRY_TTL = 8 * 60 * 60 * 1000; // 8 hours
    private static final Collection<String> DEFAULT_JVM_ARGS = Arrays.asList("-Xmx512m",
            "-Djavax.el.varArgs=true", "-Djava.security.egd=file:/dev/./urandom");

    private final Map<JobType, JobExecutor> jobExecutors;
    private final ExecutorService executor;
    private final LogManager logManager;
    private final Configuration cfg;

    private final Map<String, Future<?>> executions = new HashMap<>();
    private final Cache<String, JobStatus> statuses = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();
    private final Cache<String, Path> attachments = CacheBuilder.newBuilder()
            .expireAfterAccess(JOB_ENTRY_TTL, TimeUnit.MILLISECONDS)
            .build();

    private final Object mutex = new Object();

    @Inject
    public ExecutionManager(@Named("executionPool") ExecutorService executor,
                            JarJobExecutor jarJobExecutor,
                            LogManager logManager,
                            Configuration cfg) {

        this.executor = executor;

        this.logManager = logManager;
        this.cfg = cfg;
        this.jobExecutors = new HashMap<>();

        jobExecutors.put(JobType.JAR, jarJobExecutor);
        jobExecutors.put(JobType.JUNIT_GROOVY, new JunitGroovyJobExecutor());
    }

    public String start(InputStream payload, JobType type, String entryPoint) throws ExecutionException {
        JobExecutor exec = jobExecutors.get(type);
        if (exec == null) {
            throw new IllegalArgumentException("Unknown job type: " + type);
        }

        String id = UUID.randomUUID().toString();
        logManager.log(id, "Job type: %s", type);

        Path tmpDir = extract(id, payload);

        Map<String, Object> agentParams = getAgentParameters(tmpDir);
        Collection<String> jvmArgs = (Collection<String>) agentParams.getOrDefault(Constants.JVM_ARGS_KEY, DEFAULT_JVM_ARGS);
        log.info("start ['{}'] -> JVM args: {}", id, jvmArgs);

        synchronized (mutex) {
            statuses.put(id, JobStatus.RUNNING);
        }

        Future<?> f = executor.submit(() -> {
            try {
                exec.exec(id, tmpDir, entryPoint, jvmArgs);
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

            synchronized (mutex) {
                Path attachmentsDir = tmpDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME);
                if (Files.exists(attachmentsDir)) {
                    attachments.put(id, attachmentsDir);
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

    public Path zipAttachments(String id) throws IOException {
        Path src;
        synchronized (mutex) {
            src = attachments.getIfPresent(id);
        }

        if (src == null || !Files.exists(src)) {
            return null;
        }

        Path tmpFile = Files.createTempFile("attachments", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmpFile))) {
            IOUtils.zip(zip, src);
        }

        return tmpFile;
    }

    private Path extract(String id, InputStream in) throws ExecutionException {
        Path baseDir = cfg.getPayloadDir();
        try {
            Path dst = baseDir.resolve(id);
            Files.createDirectories(dst);

            try (ZipInputStream zip = new ZipInputStream(in)) {
                IOUtils.unzip(zip, dst);
                return dst;
            }
        } catch (IOException e) {
            throw new ExecutionException("Error while unpacking a payload", e);
        }
    }

    private static Map<String, Object> getAgentParameters(Path payload) throws ExecutionException {
        Path p = payload.resolve(Constants.AGENT_PARAMS_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptyMap();
        }

        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        } catch (IOException e) {
            throw new ExecutionException("Error while reading an agent parameters file", e);
        }
    }
}
