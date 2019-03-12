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
import com.walmartlabs.concord.agent.logging.RedirectedProcessLog;
import com.walmartlabs.concord.agent.postprocessing.JobPostProcessor;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.dependencymanager.DependencyEntity;
import com.walmartlabs.concord.dependencymanager.DependencyManager;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.DependencyRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.project.InternalConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    private final RunnerJobExecutorConfiguration cfg;
    protected final DependencyManager dependencyManager;
    private final DefaultDependencies defaultDependencies;
    private final List<JobPostProcessor> postProcessors;
    private final ExecutorService executor;

    private final ProcessPool pool;

    public RunnerJobExecutor(RunnerJobExecutorConfiguration cfg,
                             DependencyManager dependencyManager,
                             DefaultDependencies defaultDependencies,
                             List<JobPostProcessor> postProcessors,
                             ExecutorService executor) {

        this.cfg = cfg;
        this.dependencyManager = dependencyManager;
        this.defaultDependencies = defaultDependencies;
        this.postProcessors = postProcessors;
        this.executor = executor;

        this.pool = new ProcessPool(cfg.maxPreforkAge, cfg.maxPreforkCount);
    }

    public JobInstance exec(RunnerJob job) throws Exception {
        // prepare and start a new JVM of use a pre-forked one
        ProcessEntry pe;
        try {
            // resolve and download the dependencies
            Collection<Path> resolvedDeps = resolveDeps(job);

            pe = buildProcessEntry(job, resolvedDeps);
        } catch (Exception e) {
            log.warn("exec ['{}'] -> process error: {}", job.getInstanceId(), e.getMessage());

            job.getLog().error("Process startup error: {}", e.getMessage());
            job.getLog().flush();

            throw e;
        }

        // continue the execution in a separate thread to make the process cancellable
        Future<?> f = executor.submit(() -> {
            try {
                exec(job, pe);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // return a handle that can be used to cancel the process or wait for its completion
        return new JobInstanceImpl(f, pe.getProcess());
    }

    protected ProcessEntry buildProcessEntry(RunnerJob job, Collection<Path> resolvedDeps) throws Exception {
        ProcessEntry pe;
        if (canUsePrefork(job)) {
            String[] cmd = createCmd(job, resolvedDeps);
            pe = fork(job, cmd);
        } else {
            log.info("start ['{}'] -> can't use pre-forked instances", job.getInstanceId());
            String[] cmd = createCmd(job, resolvedDeps);
            Path procDir = IOUtils.createTempDir("onetime");
            pe = startOneTime(job, cmd, procDir);
        }
        return pe;
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
                handleError(job, proc, e.getMessage());
                throw new ExecutionException("Error while executing a job: " + e.getMessage());
            }

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
                handleError(job, proc, e.getMessage());
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

    private Collection<Path> resolveDeps(RunnerJob job) throws IOException, ExecutionException {
        long t1 = System.currentTimeMillis();

        // combine the default dependencies and the process' dependencies
        Collection<URI> uris = Stream.concat(defaultDependencies.getDependencies().stream(), JobDependencies.get(job).stream())
                .collect(Collectors.toList());

        Collection<DependencyEntity> deps = dependencyManager.resolve(uris);

        // check the resolved dependencies against the current policy
        validateDependencies(job, deps);

        Collection<Path> paths = deps.stream()
                .map(DependencyEntity::getPath)
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
        Map<String, Object> policyRules = readPolicyRules(job);
        if (policyRules.isEmpty()) {
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

    private String[] createCmd(RunnerJob job, Collection<Path> deps) throws IOException {
        Path depsFile = storeDeps(deps);

        RunnerCommandBuilder runner = new RunnerCommandBuilder()
                .javaCmd(cfg.agentJavaCmd)
                .workDir(job.getPayloadDir())
                .agentId(cfg.agentId)
                .serverApiBaseUrl(cfg.serverApiBaseUrl)
                .securityManagerEnabled(cfg.runnerSecurityManagerEnabled)
                .dependencies(depsFile)
                .debug(job.isDebugMode())
                .runnerPath(cfg.runnerPath.toAbsolutePath());

        return runner.build();
    }

    private ProcessEntry fork(RunnerJob job, String[] cmd) throws ExecutionException, IOException {
        long t1 = System.currentTimeMillis();

        HashCode hc = hash(cmd);

        // take a "pre-forked" JVM from the pool or start a new one
        ProcessEntry entry = pool.take(hc, () -> {
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

    protected Path storeDeps(Collection<Path> dependencies) throws IOException {
        List<String> deps = dependencies.stream()
                .map(p -> p.toAbsolutePath().toString())
                .collect(Collectors.toList());

        HashCode depsHash = hash(deps.toArray(new String[0]));

        Path result = cfg.dependencyListDir.resolve(depsHash.toString() + ".deps");
        if (Files.exists(result)) {
            return result;
        }

        Files.write(result,
                (Iterable<String>) deps.stream()::iterator,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return result;
    }

    @Override
    public String toString() {
        return "RunnerJobExecutor";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readPolicyRules(RunnerJob job) throws IOException {
        Path workDir = job.getPayloadDir();

        Path policyFile = workDir.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME)
                .resolve(InternalConstants.Files.POLICY_FILE_NAME);

        if (!Files.exists(policyFile)) {
            return Collections.emptyMap();
        }

        return new ObjectMapper().readValue(policyFile.toFile(), Map.class);
    }

    private static boolean canUsePrefork(RunnerJob job) {
        Path workDir = job.getPayloadDir();

        if (Files.exists(workDir.resolve(InternalConstants.Files.LIBRARIES_DIR_NAME))) {
            // the process supplied its own libraries, can't use preforking
            return false;
        }

        // the process supplied its own JVM parameters, can't use preforking
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
            RedirectedProcessLog processLog = job.getLog();

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

            ProcessLog processLog = job.getLog();
            processLog.delete();
        }
    }

    private static void writeInstanceId(UUID instanceId, Path dst) throws IOException {
        Path idPath = dst.resolve(InternalConstants.Files.INSTANCE_ID_FILE_NAME);
        Files.write(idPath, instanceId.toString().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    }

    public static class RunnerJobExecutorConfiguration {

        private final String agentId;
        private final String serverApiBaseUrl;
        private final String agentJavaCmd;
        private final Path dependencyListDir;
        private final Path runnerPath;
        private boolean runnerSecurityManagerEnabled;
        private final long maxPreforkAge;
        private final int maxPreforkCount;

        public RunnerJobExecutorConfiguration(String agentId,
                                              String serverApiBaseUrl,
                                              String agentJavaCmd,
                                              Path dependencyListDir,
                                              Path runnerPath,
                                              boolean isRunnerSecurityManagerEnabled,
                                              long maxPreforkAge,
                                              int maxPreforkCount) {

            this.agentId = agentId;
            this.serverApiBaseUrl = serverApiBaseUrl;
            this.agentJavaCmd = agentJavaCmd;
            this.dependencyListDir = dependencyListDir;
            this.runnerPath = runnerPath;
            this.runnerSecurityManagerEnabled = isRunnerSecurityManagerEnabled;
            this.maxPreforkAge = maxPreforkAge;
            this.maxPreforkCount = maxPreforkCount;
        }

        public Path getRunnerPath() {
            return runnerPath;
        }

        public String getAgentId() {
            return agentId;
        }

        public String getServerApiBaseUrl() {
            return serverApiBaseUrl;
        }

        public boolean isRunnerSecurityManagerEnabled() {
            return runnerSecurityManagerEnabled;
        }

        public Path getDependencyListDir() {
            return dependencyListDir;
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