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
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
    private static final Collection<String> DEFAULT_JVM_ARGS = Arrays.asList(
            "-Xmx512m",
            "-Djavax.el.varArgs=true",
            "-Djava.security.egd=file:/dev/./urandom",
            "-Djava.net.preferIPv4Stack=true");

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

    @SuppressWarnings("unchecked")
    public void start(String instanceId, JobType type, String entryPoint, InputStream payload) throws ExecutionException {
        JobExecutor exec = jobExecutors.get(type);
        if (exec == null) {
            throw new IllegalArgumentException("Unknown job type: " + type);
        }

        // remove the previous log file
        // e.g. left after the execution was suspended
        logManager.delete(instanceId);

        Path tmpDir = extract(instanceId, payload);

        Map<String, Object> agentParams = getAgentParameters(tmpDir);
        Collection<String> jvmArgs = (Collection<String>) agentParams.getOrDefault(Constants.JVM_ARGS_KEY, DEFAULT_JVM_ARGS);
        log.info("start ['{}'] -> JVM args: {}", instanceId, jvmArgs);

        Collection<String> deps = getDependencies(tmpDir);
        collectDependencies(instanceId, tmpDir, deps);

        synchronized (mutex) {
            statuses.put(instanceId, JobStatus.RUNNING);
        }

        Future<?> f = executor.submit(() -> {
            try {
                exec.exec(instanceId, tmpDir, entryPoint, jvmArgs);
                synchronized (mutex) {
                    statuses.put(instanceId, JobStatus.COMPLETED);
                }
            } catch (Exception e) {
                log.error("start ['{}', {}, '{}'] -> failed", instanceId, type, entryPoint, e);
                synchronized (mutex) {
                    JobStatus s = statuses.getIfPresent(instanceId);
                    if (s != JobStatus.CANCELLED) {
                        statuses.put(instanceId, JobStatus.FAILED);
                    }
                }
            } finally {
                synchronized (ExecutionManager.this) {
                    executions.remove(instanceId);
                }
            }

            synchronized (mutex) {
                Path attachmentsDir = tmpDir.resolve(Constants.JOB_ATTACHMENTS_DIR_NAME);
                if (Files.exists(attachmentsDir)) {
                    attachments.put(instanceId, attachmentsDir);
                }
            }
        });

        synchronized (mutex) {
            executions.put(instanceId, f);
        }
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

    public void removeAttachments(String id) throws IOException {
        Path p;
        synchronized (mutex) {
            p = attachments.getIfPresent(id);
        }

        if (p == null || !Files.exists(p)) {
            return;
        }

        IOUtils.deleteRecursively(p);
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

    @SuppressWarnings("unchecked")
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

    @SuppressWarnings("unchecked")
    private static Collection<String> getDependencies(Path payload) throws ExecutionException {
        Path p = payload.resolve(Constants.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptyList();
        }

        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> m = om.readValue(in, Map.class);

            Collection<String> deps = (Collection<String>) m.get(Constants.DEPENDENCIES_KEY);
            return deps != null ? deps : Collections.emptyList();
        } catch (IOException e) {
            throw new ExecutionException("Error while reading a list of dependencies", e);
        }
    }

    private static void collectDependencies(String instanceId, Path baseDir, Collection<String> deps) throws ExecutionException {
        if (deps.isEmpty()) {
            return;
        }

        Path libDir = baseDir.resolve(Constants.LIBRARIES_DIR_NAME);
        if (!Files.exists(libDir)) {
            try {
                Files.createDirectories(libDir);
            } catch (IOException e) {
                throw new ExecutionException("Dependencies processing error", e);
            }
        }

        // TODO collect all errors before throwing an exception
        for (String d : deps) {
            URL url;
            try {
                url = new URL(d);
            } catch (MalformedURLException e) {
                throw new ExecutionException("Invalid dependency URL: " + d, e);
            }

            String name = getLastPart(url);
            Path dst = libDir.resolve(name);
            if (Files.exists(dst)) {
                log.warn("collectDependencies ['{}'] -> library already exists: {}", instanceId, dst);
                continue;
            }

            try (InputStream in = url.openStream();
                 OutputStream out = Files.newOutputStream(dst, StandardOpenOption.CREATE)) {
                IOUtils.copy(in, out);
            } catch (IOException e) {
                throw new ExecutionException("Error while copying a dependency: " + d, e);
            }
        }
    }

    private static String getLastPart(URL url) {
        String p = url.getPath();
        int idx = p.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < p.length()) {
            return p.substring(idx + 1);
        }
        throw new IllegalArgumentException("Invalid URL: " + url);
    }
}
