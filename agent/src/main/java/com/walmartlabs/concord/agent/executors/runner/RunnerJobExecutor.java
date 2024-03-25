package com.walmartlabs.concord.agent.executors.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.walmartlabs.concord.agent.ConfiguredJobRequest;
import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.agent.JobInstance;
import com.walmartlabs.concord.agent.Utils;
import com.walmartlabs.concord.agent.executors.JobExecutor;
import com.walmartlabs.concord.agent.executors.runner.ProcessPool.ProcessEntry;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.logging.ProcessLogFactory;
import com.walmartlabs.concord.agent.remote.AttachmentsUploader;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Posix;
import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.dependencymanager.ProgressListener;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.DependencyRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.runtime.common.cfg.RunnerConfiguration;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.MapUtils;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.common.DockerProcessBuilder.CONCORD_DOCKER_LOCAL_MODE_KEY;

/**
 * Executes jobs using concord-runner runtime.
 */
public class RunnerJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunnerJobExecutor.class);

    protected final DependencyManager dependencyManager;

    private final RunnerJobExecutorConfiguration cfg;
    private final DefaultDependencies defaultDependencies;
    private final AttachmentsUploader attachmentsUploader;
    private final ProcessPool processPool;
    private final ProcessLogFactory logFactory;
    private final ExecutorService executor;

    private final ObjectMapper objectMapper;

    private final int majorJavaVersion;

    public RunnerJobExecutor(RunnerJobExecutorConfiguration cfg,
                             DependencyManager dependencyManager,
                             DefaultDependencies defaultDependencies,
                             AttachmentsUploader attachmentsUploader,
                             ProcessPool processPool,
                             ProcessLogFactory processLogFactory,
                             ExecutorService executor) {

        this.cfg = cfg;
        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.attachmentsUploader = attachmentsUploader;
        this.processPool = processPool;
        this.logFactory = processLogFactory;
        this.executor = executor;

        // sort JSON keys for consistency
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

        this.majorJavaVersion = getMajorJavaVersion(cfg.javaCmd());
    }

    private static int getMajorJavaVersion(String javaCmd) {
        try {
            Process process = new ProcessBuilder(javaCmd, "-version")
                    .start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("`java -version` exited with " + exitCode);
            }

            String version;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                version = reader.readLine();
            }

            int start = version.indexOf("\"");
            int end = version.indexOf(".", start + 1);
            if (start < 0 || start + 1 >= version.length() || end < 0) {
                throw new RuntimeException("Unknown version string: " + version);
            }

            int major;
            try {
                major = Integer.parseInt(version.substring(start + 1, end));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Unknown version string: " + version);
            }

            return major;
        } catch (Exception e) {
            throw new RuntimeException("Can't determine the target Java runtime version", e);
        }
    }

    @Override
    public JobInstance exec(ConfiguredJobRequest jobRequest) throws Exception {
        RunnerJob job = RunnerJob.from(cfg, jobRequest, logFactory);
        return exec(job);
    }

    private JobInstance exec(RunnerJob job) throws Exception {
        // prepare and start a new JVM of use a pre-forked one
        ProcessEntry pe;
        try {
            // resolve and download the dependencies
            Collection<String> resolvedDeps;
            if (cfg.dependencyResolveTimeout() != null) {
                Duration timeout = Objects.requireNonNull(cfg.dependencyResolveTimeout());
                RunnerJob finalJob = job;
                Future<Collection<String>> future = executor.submit(() -> resolveDeps(finalJob));
                try {
                    resolvedDeps = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | TimeoutException e) {
                    future.cancel(true);
                    throw new RuntimeException("Timeout resolving dependencies");
                }
            } else {
                resolvedDeps = resolveDeps(job);
            }

            job = job.withDependencies(resolvedDeps);

            pe = buildProcessEntry(job);
        } catch (Throwable e) {
            log.warn("exec ['{}'] -> process error: {}", job.getInstanceId(), e.getMessage(), e);

            job.getLog().error("Process startup error: {}", e.getMessage(), e);

            cleanup(job);

            throw e;
        }

        // continue the execution in a separate thread to make the process cancellable
        RunnerJob _job = job;
        Future<?> f = executor.submit(() -> {
            boolean uploadAttachmentsOnError = true;

            try {
                exec(_job, pe);

                uploadAttachmentsOnError = false;
                uploadAttachments(_job.getInstanceId(), pe);
            } catch (Throwable t) {
                if (uploadAttachmentsOnError) {
                    try {
                        uploadAttachments(_job.getInstanceId(), pe);
                    } catch (Exception e) {
                        // ignore
                    }
                }

                throw new RuntimeException(t);
            } finally {
                persistWorkDir(_job.getInstanceId(), pe.getWorkDir());
                cleanup(_job.getInstanceId(), pe);
                cleanup(_job);
            }
        });

        // return a handle that can be used to cancel the process or wait for its completion
        return new JobInstanceImpl(f, pe.getProcess(), cfg.cleanRunnerDescendants());
    }

    private void persistWorkDir(UUID instanceId, Path src) {
        Path persistentWorkDir = cfg.persistentWorkDir();

        if (persistentWorkDir == null) {
            return;
        }

        Path dst = persistentWorkDir.resolve(instanceId.toString());

        try {
            if (!Files.exists(dst)) {
                Files.createDirectories(dst);
            }

            log.info("exec ['{}'] -> persisting the payload directory into {}...", instanceId, dst);
            IOUtils.copy(src, dst);

            // persistentWorkDir is mostly useful when the Agent is running in a container
            // typically it is running as PID 456 - all files created by the process
            // are created using PID 456 and won't be readable by the host user

            // therefore, we need to make all files readable by all users
            // and that's why runner.persistentWorkDir shouldn't be used in prod

            try(Stream<Path> walk = Files.walk(dst)) {
                walk.forEach(f -> {
                    try {
                        if (Files.isDirectory(f)) {
                            Files.setPosixFilePermissions(f, Posix.posix(0755));
                        } else if (Files.isRegularFile(f)) {
                            Files.setPosixFilePermissions(f, Posix.posix(0644));
                        }
                    } catch (IOException e) {
                        log.warn("persistWorkDir -> can't update permissions for {}: {}", f, e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            log.warn("persistWorkDir -> failed to copy {} into {}: {}", src, dst, e.getMessage());
        }
    }

    private void uploadAttachments(UUID instanceId, ProcessEntry pe) {
        try {
            attachmentsUploader.upload(instanceId, pe.getWorkDir());
        } catch (Exception e) {
            log.error("uploadAttachments ['{}'] -> error: {}", instanceId, e.getMessage());
            throw new RuntimeException("Error while uploading attachments: " + e.getMessage());
        }
    }

    private void cleanup(UUID instanceId, ProcessEntry pe) {
        Path workDir = pe.getWorkDir();
        try {
            log.info("exec ['{}'] -> removing the working directory: {}", instanceId, workDir);
            IOUtils.deleteRecursively(workDir);
        } catch (IOException e) {
            log.warn("exec ['{}'] -> can't remove the working directory: {}", instanceId, e.getMessage());
        }
    }

    protected ProcessEntry buildProcessEntry(RunnerJob job) throws Exception {
        List<String> jvmParams = getJvmParams(job.getPayloadDir(), job.getProcessCfg());
        String[] cmd = createCmd(job, jvmParams);

        boolean prefork = canUsePrefork(job);
        if (prefork) {
            log.info("start ['{}'] -> using a pre-forked instances", job.getInstanceId());
            return fork(job, cmd);
        } else {
            return startOneTime(job, cmd);
        }
    }

    private void exec(RunnerJob job, ProcessEntry pe) throws Exception {
        // the actual OS process
        Process proc = pe.getProcess();

        UUID instanceId = job.getInstanceId();
        ProcessLog processLog = job.getLog();

        // start the log's maintenance thread (e.g. streaming to the server)
        LogStream logStream = new LogStream(job, proc);
        logStream.start();

        try {
            // save the process' log
            processLog.log(proc.getInputStream());

            // wait for the process to finish
            int code;
            try {
                code = proc.waitFor();
            } catch (Exception e) {
                // wait for the log to finish
                logStream.waitForCompletion();
                handleError(job, proc, e.getMessage());
                throw new ExecutionException("Error while executing a job: " + e.getMessage());
            }

            // wait for the log to finish
            logStream.waitForCompletion();

            if (code != 0) {
                log.warn("exec ['{}'] -> finished with {}", instanceId, code);
                handleError(job, proc, "Process exit code: " + code);
                throw new ExecutionException("Error while executing a job, process exit code: " + code);
            }

            log.info("exec ['{}'] -> finished with {}", instanceId, code);
            processLog.info("Process finished with: {}", code);
        } finally {
            // wait for the log to finish
            logStream.waitForCompletion();
        }
    }

    private void handleError(RunnerJob job, Process proc, String error) {
        job.getLog().error(error);

        if (Utils.kill(proc)) {
            log.warn("handleError ['{}'] -> killed by agent", job.getInstanceId());
        }
    }

    private Collection<String> resolveDeps(RunnerJob job) throws Exception {
        job.getLog().info("Resolving process dependencies...");

        long t1 = System.currentTimeMillis();

        // combine the default dependencies and the process' dependencies
        Collection<URI> uris = Stream.concat(defaultDependencies.getDependencies().stream(), JobDependencies.get(job).stream())
                .collect(Collectors.toList());

        uris = rewriteDependencies(job, uris);

        Collection<DependencyEntity> deps = dependencyManager.resolve(uris, new ProgressListener() {

            private final List<String> errors = Collections.synchronizedList(new ArrayList<>());

            @Override
            public void onRetry(int retryCount, int maxRetry, long interval, String cause) {
                synchronized (errors) {
                    for (String error : errors) {
                        job.getLog().warn(error);
                    }
                }

                job.getLog().warn("Error while downloading dependencies: {}", cause);
                job.getLog().info("Retrying in {}ms", interval);
            }

            @Override
            public void onTransferFailed(String error) {
                if (job.isDebugMode()) {
                    job.getLog().warn(error);
                } else {
                    errors.add(error);
                }
            }
        });

        // check the resolved dependencies against the current policy
        validateDependencies(job, deps);

        // sort dependencies to maintain consistency in runner configurations
        Collection<String> paths = deps.stream()
                .map(DependencyEntity::getPath)
                .map(p -> p.toAbsolutePath().toString())
                .sorted()
                .collect(Collectors.toList());

        long t2 = System.currentTimeMillis();

        if (job.isDebugMode()) {
            job.getLog().info("Dependency resolution took {}ms", (t2 - t1));
            logDependencies(job, paths);
        } else {
            logDependencies(job, uris);
        }

        return paths;
    }

    private Collection<URI> rewriteDependencies(RunnerJob job, Collection<URI> uris) {
        PolicyEngine policyEngine = job.getPolicyEngine();
        if (policyEngine == null) {
            return uris;
        }

        return policyEngine.getDependencyRewritePolicy()
                .rewrite(uris, (msg, from, to) -> {
                    job.getLog().info("Updating dependency from '{}' to '{}'", from, to);
                    if (msg != null) {
                        job.getLog().warn(msg);
                    }
                });
    }

    private void validateDependencies(RunnerJob job, Collection<DependencyEntity> resolvedDepEntities) throws ExecutionException {
        PolicyEngine policyEngine = job.getPolicyEngine();
        if (policyEngine == null) {
            return;
        }

        ProcessLog processLog = job.getLog();

        processLog.info("Checking the dependency policy...");

        CheckResult<DependencyRule, DependencyEntity> result = policyEngine.getDependencyPolicy().check(resolvedDepEntities);
        result.getWarn().forEach(d ->
                processLog.warn("Potentially restricted artifact '{}' (dependency policy: {})", d.getEntity(), d.getRule().msg()));
        result.getDeny().forEach(d ->
                processLog.warn("Artifact '{}' is forbidden by the dependency policy {}", d.getEntity(), d.getRule().msg()));

        if (!result.getDeny().isEmpty()) {
            throw new ExecutionException("Found restricted dependencies");
        }
    }

    private void logDependencies(RunnerJob job, Collection<?> deps) {
        if (deps == null || deps.isEmpty()) {
            job.getLog().info("No external dependencies.");
            return;
        }

        List<String> l = deps.stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        StringBuilder b = new StringBuilder();
        for (String s : l) {
            b.append("\n\t").append(s);
        }

        job.getLog().info("Dependencies: {}", b);
    }

    private String[] createCmd(RunnerJob job, List<String> jvmParams) throws IOException {
        Path runnerCfgFile = storeRunnerCfg(cfg.runnerCfgDir(), job.getRunnerCfg());
        return new RunnerCommandBuilder()
                .javaCmd(cfg.javaCmd())
                .logLevel(getLogLevel(job))
                .extraDockerVolumesFile(createExtraDockerVolumesFile(job))
                .exposeDockerDaemon(cfg.exposeDockerDaemon())
                .runnerPath(cfg.runnerPath().toAbsolutePath())
                .runnerCfgPath(runnerCfgFile.toAbsolutePath())
                .mainClass(cfg.runnerMainClass())
                .jvmParams(jvmParams)
                .majorJavaVersion(this.majorJavaVersion)
                .build();
    }

    private ProcessEntry fork(RunnerJob job, String[] cmd) throws ExecutionException, IOException {
        long t1 = System.currentTimeMillis();

        HashCode hc = hash(cmd);

        // take a "pre-forked" JVM from the pool or start a new one
        ProcessEntry entry = processPool.take(hc, () -> {
            // can't use workDirBase, "preforks" start before they receive their process payload
            // create a new temporary directory
            Path workDir = IOUtils.createTempDir("workDir");
            return start(workDir, cmd);
        });

        // the job's payload directory containing all files from the process' state snapshot and/or the repository's data
        Path src = job.getPayloadDir();
        // the process' workDir
        Path dst = entry.getWorkDir();
        // TODO use move
        IOUtils.copy(src, dst);

        writeInstanceId(job.getInstanceId(), dst);

        long t2 = System.currentTimeMillis();

        if (job.isDebugMode()) {
            job.getLog().info("Forking a VM took {}ms", (t2 - t1));
        }

        return entry;
    }

    protected ProcessEntry startOneTime(RunnerJob job, String[] cmd) throws IOException {
        // create the parent directory of the process' ${workDir}
        Path workDir = cfg.workDirBase().resolve(job.getInstanceId().toString());
        if (!Files.exists(workDir)) {
            Files.createDirectories(workDir);
        }

        // the job's payload directory, contains all files from the state snapshot including imports
        Path src = job.getPayloadDir();

        Files.move(src, workDir, StandardCopyOption.ATOMIC_MOVE);

        writeInstanceId(job.getInstanceId(), workDir);

        return start(workDir, cmd);
    }

    private ProcessEntry start(Path workDir, String[] cmd) throws IOException {
        log.info("start -> {}, {}", workDir, String.join(" ", cmd));

        ProcessBuilder b = new ProcessBuilder()
                .directory(workDir.toFile())
                .command(cmd)
                .redirectErrorStream(true);

        // TODO constants
        Map<String, String> env = b.environment();
        env.put(IOUtils.TMP_DIR_KEY, IOUtils.TMP_DIR.toAbsolutePath().toString());
        env.put("_CONCORD_ATTACHMENTS_DIR", workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .toAbsolutePath().toString());

        // pass through the docker mode
        String dockerMode = System.getenv(CONCORD_DOCKER_LOCAL_MODE_KEY);
        if (dockerMode != null) {
            log.debug("start -> using Docker mode: {}", dockerMode);
            env.put(CONCORD_DOCKER_LOCAL_MODE_KEY, dockerMode);
        }

        Process p = b.start();
        return new ProcessEntry(p, workDir);
    }

    protected Path storeRunnerCfg(Path baseDir, RunnerConfiguration runnerCfg) throws IOException {
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }

        byte[] data = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(runnerCfg);
        HashCode hc = Hashing.sha256().hashBytes(data);

        Path cfgFile = baseDir.resolve(hc + ".json");
        if (!Files.exists(cfgFile)) {
            Files.write(cfgFile, data);
        }

        return cfgFile;
    }

    @Override
    public String toString() {
        return "RunnerJobExecutor";
    }

    private Path createExtraDockerVolumesFile(RunnerJob job) throws IOException {
        List<String> l = cfg.extraDockerVolumes();
        if (l.isEmpty()) {
            return null;
        }

        Path workDir = job.getPayloadDir();
        Path p = workDir.resolve(".extraDockerVolumes");
        Files.write(p, l, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return workDir.relativize(p);
    }

    @SuppressWarnings("unchecked")
    private List<String> getJvmParams(Path workDir, Map<String, Object> processCfg) {
        // the _agent.json file takes precedence
        Path p = workDir.resolve(Constants.Agent.AGENT_PARAMS_FILE_NAME);
        if (Files.exists(p)) {
            try (InputStream in = Files.newInputStream(p)) {
                Map<String, Object> m = objectMapper.readValue(in, Map.class);
                List<String> l = MapUtils.getList(m, Constants.Agent.JVM_ARGS_KEY, null);
                if (l != null) {
                    return l;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // check the `configuration.requirements.jvm` next
        List<String> l = getJvmArgsFromConfig(processCfg);
        if (l != null) {
            return l;
        }

        // fallback to the default parameters
        return cfg.jvmParams();
    }

    private boolean canUsePrefork(RunnerJob job) {
        if (!cfg.preforkEnabled()) {
            return false;
        }

        Path workDir = job.getPayloadDir();

        if (Files.exists(workDir.resolve(Constants.Files.LIBRARIES_DIR_NAME))) {
            // the process supplied its own libraries, can't use preforking
            return false;
        }

        // the process supplied its own JVM parameters in concord.yml, can't use preforking
        List<String> jvmExtraArgs = getJvmArgsFromConfig(job.getProcessCfg());
        if (jvmExtraArgs != null) {
            return false;
        }

        // the process supplied its own JVM parameters in _agent.json, can't use preforking
        return !Files.exists(workDir.resolve(Constants.Agent.AGENT_PARAMS_FILE_NAME));
    }

    private static List<String> getJvmArgsFromConfig(Map<String, Object> processCfg) {
        Map<String, Object> requirements = MapUtils.get(processCfg, Constants.Request.REQUIREMENTS, null);
        if (requirements == null) {
            return null;
        }

        // TODO constants?
        Map<String, Object> jvm = MapUtils.get(requirements, "jvm", null);
        if (jvm == null) {
            return null;
        }

        // TODO constants?
        List<String> extraArgs = MapUtils.getList(jvm, "extraArgs", null);
        if (extraArgs == null || extraArgs.isEmpty()) {
            return null;
        }

        return extraArgs;
    }

    private static String getLogLevel(RunnerJob job) {
        RunnerConfiguration cfg = job.getRunnerCfg();
        if (cfg == null) {
            return null;
        }

        String logLevel = cfg.logLevel();
        if (logLevel == null) {
            return null;
        }

        return logLevel.toUpperCase();
    }

    private static HashCode hash(String[] as) {
        HashFunction f = Hashing.sha256();
        Hasher h = f.newHasher();
        for (String s : as) {
            h.putString(s, Charsets.UTF_8);
        }
        return h.hash();
    }

    private static void cleanup(RunnerJob job) {
        try {
            job.getLog().delete();
        } catch (Exception e) {
            log.warn("cleanup [{}] -> error while cleaning up the process logs: {}", job.getInstanceId(), e.getMessage());
        }
    }

    /**
     * A tiny wrapper to simplify working with the log streaming.
     */
    private class LogStream {

        private final RunnerJob job;
        private final Process proc;

        private Future<?> f;
        private transient boolean doStop = false;

        private LogStream(RunnerJob job, Process proc) {
            this.job = job;
            this.proc = proc;
        }

        /**
         * Starts the log streaming in a separate thread.
         */
        public void start() {
            RunnerLog processLog = job.getLog();

            f = executor.submit(() -> {
                try {
                    processLog.run(() -> doStop);
                } catch (Exception e) {
                    handleError(job, proc, e.getMessage());
                }
            });
        }

        /**
         * Waits for the log stream to finish and removes the log file.
         */
        public void waitForCompletion() {
            this.doStop = true;

            try {
                f.get(1, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("waitForCompletion -> timeout waiting for the log stream of {}", job.getInstanceId());
            }
        }
    }

    private static void writeInstanceId(UUID instanceId, Path dst) throws IOException {
        Path idPath = dst.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        Files.write(idPath, instanceId.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    public interface RunnerJobExecutorConfiguration {

        String agentId();

        String serverApiBaseUrl();

        String javaCmd();

        List<String> jvmParams();

        Path dependencyListDir();

        Path dependencyCacheDir();

        @Nullable
        Duration dependencyResolveTimeout();

        Path workDirBase();

        Path runnerPath();

        Path runnerCfgDir();

        String runnerMainClass();

        boolean runnerSecurityManagerEnabled();

        boolean segmentedLogs();

        @Value.Default
        default List<String> extraDockerVolumes() {
            return Collections.emptyList();
        }

        @Value.Default
        default Boolean exposeDockerDaemon() {
            return true;
        }

        long maxHeartbeatInterval();

        @Nullable
        Path persistentWorkDir();

        boolean preforkEnabled();

        boolean cleanRunnerDescendants();

        static ImmutableRunnerJobExecutorConfiguration.Builder builder() {
            return ImmutableRunnerJobExecutorConfiguration.builder();
        }
    }

    private static class JobInstanceImpl implements JobInstance {

        private final Future<?> f;
        private final Process proc;
        private final boolean cleanRunnerDescendants;

        private transient boolean cancelled = false;

        private JobInstanceImpl(Future<?> f, Process proc, boolean cleanRunnerDescendants) {
            this.f = f;
            this.proc = proc;
            this.cleanRunnerDescendants = cleanRunnerDescendants;
        }

        @Override
        public void waitForCompletion() throws Exception {
            f.get();
        }

        @Override
        public void cancel() {
            if (f.isCancelled() || f.isDone()) {
                return;
            }

            cancelled = true;

            Utils.kill(proc, cleanRunnerDescendants);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
