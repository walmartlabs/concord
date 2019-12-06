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
import com.walmartlabs.concord.agent.ExecutionException;
import com.walmartlabs.concord.agent.JobInstance;
import com.walmartlabs.concord.agent.Utils;
import com.walmartlabs.concord.agent.executors.runner.ProcessPool.ProcessEntry;
import com.walmartlabs.concord.agent.logging.ProcessLog;
import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.DependencyRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.runner.model.RunnerConfiguration;
import com.walmartlabs.concord.sdk.MapUtils;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.common.DockerProcessBuilder.CONCORD_DOCKER_LOCAL_MODE_KEY;

/**
 * Executes jobs using concord-runner runtime.
 */
public class RunnerJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunnerJobExecutor.class);

    protected final DependencyManager dependencyManager;

    private final RunnerJobExecutorConfiguration cfg;
    private final DefaultDependencies defaultDependencies;
    private final List<JobPostProcessor> postProcessors;
    private final ProcessPool processPool;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    public RunnerJobExecutor(RunnerJobExecutorConfiguration cfg,
                             DependencyManager dependencyManager,
                             DefaultDependencies defaultDependencies,
                             List<JobPostProcessor> postProcessors,
                             ProcessPool processPool,
                             ExecutorService executor) {

        this.cfg = cfg;
        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.postProcessors = postProcessors;
        this.processPool = processPool;
        this.executor = executor;

        // sort JSON keys for consistency
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public JobInstance exec(RunnerJob job) throws Exception {
        // prepare and start a new JVM of use a pre-forked one
        ProcessEntry pe;
        try {
            // resolve and download the dependencies
            Collection<String> resolvedDeps = resolveDeps(job);
            job = job.withDependencies(resolvedDeps);

            pe = buildProcessEntry(job);
        } catch (Exception e) {
            log.warn("exec ['{}'] -> process error: {}", job.getInstanceId(), e.getMessage());

            job.getLog().error("Process startup error: {}", e.getMessage());

            cleanup(job);

            throw e;
        }

        // continue the execution in a separate thread to make the process cancellable
        RunnerJob _job = job;
        Future<?> f = executor.submit(() -> {
            try {
                exec(_job, pe);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                cleanup(_job);
            }
        });

        // return a handle that can be used to cancel the process or wait for its completion
        return new JobInstanceImpl(f, pe.getProcess());
    }

    protected ProcessEntry buildProcessEntry(RunnerJob job) throws Exception {
        List<String> jvmParams = getAgentJvmParams(job.getPayloadDir(), job.getProcessCfg());
        String[] cmd = createCmd(job, jvmParams);

        boolean prefork = canUsePrefork(job);
        if (prefork) {
            return fork(job, cmd);
        } else {
            log.info("start ['{}'] -> can't use pre-forked instances", job.getInstanceId());
            Path procDir = IOUtils.createTempDir("onetime");
            return startOneTime(job, cmd, procDir);
        }
    }

    private void exec(RunnerJob job, ProcessEntry pe) throws Exception {
        // the actual OS process
        Process proc = pe.getProcess();
        Path procDir = pe.getProcDir();

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

            Path payloadDir = procDir.resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);

            // run all job post processors, e.g. the attachment uploader
            try {
                for (JobPostProcessor p : postProcessors) {
                    p.process(instanceId, payloadDir);
                }
            } catch (ExecutionException e) {
                log.warn("exec ['{}'] -> postprocessing error: {}", instanceId, e.getMessage());
                job.getLog().error(e.getMessage());
            }

            try {
                log.info("exec ['{}'] -> removing the working directory: {}", instanceId, procDir);
                IOUtils.deleteRecursively(procDir);
            } catch (IOException e) {
                log.warn("exec ['{}'] -> can't remove the working directory: {}", instanceId, e.getMessage());
            }
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

        Collection<DependencyEntity> deps = dependencyManager.resolve(uris, (retryCount, maxRetry, interval, cause) -> {
            job.getLog().warn("Error while downloading dependencies: {}", cause);
            job.getLog().info("Retrying in {}ms", interval);
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

    private void validateDependencies(RunnerJob job, Collection<DependencyEntity> resolvedDepEntities) throws IOException, ExecutionException {
        PolicyEngineRules policyRules = readPolicyRules(job);
        if (policyRules == null) {
            return;
        }

        ProcessLog processLog = job.getLog();

        processLog.info("Checking the dependency policy...");

        CheckResult<DependencyRule, DependencyEntity> result = new PolicyEngine(policyRules).getDependencyPolicy().check(resolvedDepEntities);
        result.getWarn().forEach(d ->
                processLog.info("Potentially restricted artifact '{}' (dependency policy: {})", d.getEntity().toString(), d.getRule().toString()));
        result.getDeny().forEach(d ->
                processLog.info("Artifact '{}' is forbidden by the dependency policy {}", d.getEntity().toString(), d.getRule().toString()));

        if (!result.getDeny().isEmpty()) {
            throw new ExecutionException("Found forbidden dependencies");
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

        RunnerCommandBuilder runner = new RunnerCommandBuilder()
                .javaCmd(cfg.agentJavaCmd())
                .logLevel(getLogLevel(job))
                .extraDockerVolumesFile(createExtraDockerVolumesFile(job))
                .runnerPath(cfg.runnerPath().toAbsolutePath())
                .runnerCfgPath(runnerCfgFile.toAbsolutePath());

        if (jvmParams != null) {
            runner.jvmParams(jvmParams);
        }

        return runner.build();
    }

    private ProcessEntry fork(RunnerJob job, String[] cmd) throws ExecutionException, IOException {
        long t1 = System.currentTimeMillis();

        HashCode hc = hash(cmd);

        // take a "pre-forked" JVM from the pool or start a new one
        ProcessEntry entry = processPool.take(hc, () -> {
            Path forkDir = IOUtils.createTempDir("prefork");
            return start(forkDir, cmd);
        });

        // the job's payload directory containing all files from the process' state snapshot and/or the repository's data
        Path src = job.getPayloadDir();
        // the VM's payload directory
        Path dst = entry.getProcDir().resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);
        // TODO use move
        IOUtils.copy(src, dst);

        writeInstanceId(job.getInstanceId(), dst);

        long t2 = System.currentTimeMillis();

        if (job.isDebugMode()) {
            job.getLog().info("Forking a VM took {}ms", (t2 - t1));
        }

        return entry;
    }

    protected ProcessEntry startOneTime(RunnerJob job, String[] cmd, Path procDir) throws IOException {
        // the job's payload directory containing all files from the process' state snapshot and/or the repository's data
        Path src = job.getPayloadDir();
        // the VM's payload directory
        Path dst = procDir.resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);
        Files.move(src, dst, StandardCopyOption.ATOMIC_MOVE);

        writeInstanceId(job.getInstanceId(), dst);

        return start(procDir, cmd);
    }

    private ProcessEntry start(Path procDir, String[] cmd) throws IOException {
        Path payloadDir = procDir.resolve(InternalConstants.Files.PAYLOAD_DIR_NAME);
        if (!Files.exists(payloadDir)) {
            Files.createDirectories(payloadDir);
        }

        log.info("start -> {}, {}", payloadDir, String.join(" ", cmd));

        ProcessBuilder b = new ProcessBuilder()
                .directory(payloadDir.toFile())
                .command(cmd)
                .redirectErrorStream(true);

        // TODO constants
        Map<String, String> env = b.environment();
        env.put(IOUtils.TMP_DIR_KEY, IOUtils.TMP_DIR.toAbsolutePath().toString());
        env.put("_CONCORD_ATTACHMENTS_DIR", payloadDir.resolve(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .toAbsolutePath().toString());

        // pass through the docker mode
        String dockerMode = System.getenv(CONCORD_DOCKER_LOCAL_MODE_KEY);
        if (dockerMode != null) {
            log.debug("start -> using Docker mode: {}", dockerMode);
            env.put(CONCORD_DOCKER_LOCAL_MODE_KEY, dockerMode);
        }

        Process p = b.start();
        return new ProcessEntry(p, procDir);
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
    private List<String> getAgentJvmParams(Path workDir, Map<String, Object> processCfg) {
        Path p = workDir.resolve(InternalConstants.Agent.AGENT_PARAMS_FILE_NAME);
        if (!Files.exists(p)) {
            // get jvm args from the process' configuration
            return getJvmArgsFromConfig(processCfg);
        }

        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            return (List<String>) m.get(InternalConstants.Agent.JVM_ARGS_KEY);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> getJvmArgsFromConfig(Map<String, Object> processCfg) {
        Map<String, Object> requirements = MapUtils.get(processCfg, InternalConstants.Request.REQUIREMENTS, null);
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

    private static PolicyEngineRules readPolicyRules(RunnerJob job) throws IOException {
        Path workDir = job.getPayloadDir();

        Path policyFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.POLICY_FILE_NAME);

        if (!Files.exists(policyFile)) {
            return null;
        }

        return new ObjectMapper().readValue(policyFile.toFile(), PolicyEngineRules.class);
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

    private static boolean canUsePrefork(RunnerJob job) {
        Path workDir = job.getPayloadDir();

        if (Files.exists(workDir.resolve(InternalConstants.Files.LIBRARIES_DIR_NAME))) {
            // the process supplied its own libraries, can't use preforking
            return false;
        }

        // the process supplied its own JVM parameters in concord.yml, can't use preforking
        List<String> jvmExtraArgs = getJvmArgsFromConfig(job.getProcessCfg());
        if (jvmExtraArgs != null) {
            return false;
        }

        // the process supplied its own JVM parameters in _agent.json, can't use preforking
        return !Files.exists(workDir.resolve(InternalConstants.Agent.AGENT_PARAMS_FILE_NAME));
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
        Path idPath = dst.resolve(InternalConstants.Files.INSTANCE_ID_FILE_NAME);
        Files.write(idPath, instanceId.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    }

    @Value.Immutable
    public interface RunnerJobExecutorConfiguration {

        String agentId();

        String serverApiBaseUrl();

        String agentJavaCmd();

        Path dependencyListDir();

        Path dependencyCacheDir();

        Path runnerPath();

        Path runnerCfgDir();

        boolean runnerSecurityManagerEnabled();

        @Value.Default
        default List<String> extraDockerVolumes() {
            return Collections.emptyList();
        }

        long maxHeartbeatInterval();

        static ImmutableRunnerJobExecutorConfiguration.Builder builder() {
            return ImmutableRunnerJobExecutorConfiguration.builder();
        }
    }

    private static class JobInstanceImpl implements JobInstance {

        private final Future<?> f;
        private final Process proc;

        private transient boolean cancelled = false;

        private JobInstanceImpl(Future<?> f, Process proc) {
            this.f = f;
            this.proc = proc;
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

            Utils.kill(proc);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}