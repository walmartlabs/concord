package com.walmartlabs.concord.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.DependencyManager;
import com.walmartlabs.concord.project.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public class JarJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(JarJobExecutor.class);

    private static final Collection<String> DEFAULT_JVM_ARGS = Arrays.asList(
            "-Xmx128m",
            "-Djavax.el.varArgs=true",
            "-Djava.security.egd=file:/dev/./urandom",
            "-Djava.net.preferIPv4Stack=true");

    private final Configuration cfg;
    private final LogManager logManager;
    private final DependencyManager dependencyManager;
    private final ExecutorService executorService;

    public JarJobExecutor(Configuration cfg, LogManager logManager, DependencyManager dependencyManager,
                          ExecutorService executorService) {

        this.cfg = cfg;
        this.logManager = logManager;
        this.dependencyManager = dependencyManager;
        this.executorService = executorService;
    }

    protected Configuration getCfg() {
        return cfg;
    }

    @Override
    public JobInstance start(String instanceId, Path workDir, String entryPoint) throws ExecutionException {
        try {
            collectDependencies(instanceId, workDir);

            String[] cmd = createCommand(instanceId, workDir, entryPoint);
            Process proc = start(instanceId, workDir, cmd);

            CompletableFuture<?> f = CompletableFuture.supplyAsync(() -> {
                try {
                    try {
                        logManager.log(instanceId, proc.getInputStream());
                    } catch (IOException e) {
                        log.warn("start ['{}', '{}'] -> error while saving a log file", instanceId, workDir);
                    }

                    int code;
                    try {
                        code = proc.waitFor();
                    } catch (Exception e) {
                        throw handleError(instanceId, workDir, proc, e.getMessage());
                    }

                    if (code != 0) {
                        log.warn("exec ['{}'] -> finished with {}", instanceId, code);
                        throw handleError(instanceId, workDir, proc, "Process exit code: " + code);
                    }

                    log.info("exec ['{}'] -> finished with {}", instanceId, code);
                    logManager.log(instanceId, "Process finished with: %s", code);

                    return code;
                } finally {
                    try {
                        postProcess(instanceId, workDir);
                    } catch (ExecutionException e) {
                        log.warn("exec ['{}'] -> postprocessing error: {}", instanceId, e.getMessage());
                        handleError(instanceId, workDir, proc, e.getMessage());
                    }
                }
            }, executorService);

            return createJobInstance(instanceId, proc, f);
        } catch (Exception e) {
            log.warn("start ['{}', '{}'] -> process startup error: {}", instanceId, workDir, e.getMessage());
            logManager.log(instanceId, "Process startup error: %s", e);

            CompletableFuture<?> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return createJobInstance(instanceId, null, f);
        }
    }

    private JobInstance createJobInstance(String instanceId, Process proc, CompletableFuture<?> f) {
        return new JobInstance() {

            @Override
            public Path logFile() {
                return logManager.open(instanceId);
            }

            @Override
            public void cancel() {
                if (!f.isCancelled()) {
                    f.cancel(true);
                }

                if (proc != null && JarJobExecutor.kill(proc)) {
                    log.warn("kill -> killed by user: {}", instanceId);
                    logManager.log(instanceId, "Killed by user");
                }
            }

            @Override
            public CompletableFuture<?> future() {
                return f;
            }
        };
    }

    protected String getMainClass(Path workDir, String entryPoint) throws ExecutionException {
        return Utils.getMainClass(workDir, entryPoint);
    }

    protected String createClassPath(String entryPoint) {
        return Constants.Files.LIBRARIES_DIR_NAME + "/*:" + entryPoint;
    }

    @SuppressWarnings("unchecked")
    protected String[] createCommand(String instanceId,
                                     Path workDir, String entryPoint) throws ExecutionException {

        Map<String, Object> agentParams = getAgentParameters(workDir);
        Collection<String> jvmArgs = (Collection<String>) agentParams.getOrDefault(Constants.Agent.JVM_ARGS_KEY, DEFAULT_JVM_ARGS);

        String javaCmd = cfg.getAgentJavaCmd();
        String classPath = createClassPath(entryPoint);
        String mainClass = getMainClass(workDir, entryPoint);

        Collection<String> cmd = new ArrayList<>();
        cmd.add(javaCmd);
        cmd.addAll(jvmArgs);
        cmd.add("-DagentId=" + cfg.getAgentId());
        cmd.add("-DinstanceId=" + instanceId);
        cmd.add("-Drpc.server.host=" + cfg.getServerHost());
        cmd.add("-Drpc.server.port=" + cfg.getServerPort());
        cmd.add("-cp");
        cmd.add(classPath);
        cmd.add(mainClass);

        return cmd.toArray(new String[cmd.size()]);
    }

    protected void postProcess(String instanceId, Path workDir) throws ExecutionException {
    }

    private Process start(String instanceId, Path workDir, String[] cmd) throws ExecutionException {
        String fullCmd = String.join(" ", cmd);

        try {
            log.info("exec ['{}', '{}'] -> executing: {}", instanceId, workDir, fullCmd);
            logManager.log(instanceId, "Starting: %s", fullCmd);

            ProcessBuilder b = new ProcessBuilder()
                    .directory(workDir.toFile())
                    .command(cmd)
                    .redirectErrorStream(true);

            Map<String, String> env = b.environment();
            env.put("_CONCORD_ATTACHMENTS_DIR", workDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                    .toAbsolutePath().toString());

            return b.start();
        } catch (IOException e) {
            log.error("exec ['{}', '{}'] -> error while starting for a process", instanceId, workDir);
            logManager.log(instanceId, "Error: %s", e);
            throw new ExecutionException("Error starting a process: " + fullCmd, e);
        }
    }

    private JobExecutorException handleError(String id, Path workDir, Process proc, String error) {
        log.warn("handleError ['{}', '{}'] -> execution error: {}", id, workDir, error);
        logManager.log(id, "Error: " + error);

        if (kill(proc)) {
            log.warn("handleError ['{}', '{}'] -> killed by agent", id, workDir);
        }

        throw new JobExecutorException("Error while executing a job: " + error);
    }

    private void collectDependencies(String instanceId, Path tmpDir) throws ExecutionException {
        Collection<String> deps = getDependencies(tmpDir);
        if (deps != null && !deps.isEmpty()) {
            logManager.log(instanceId, "Collecting dependencies...");
        }

        try {
            dependencyManager.collectDependencies(deps, tmpDir.resolve(Constants.Files.LIBRARIES_DIR_NAME));
        } catch (IOException e) {
            throw new ExecutionException("Error while collecting dependencies", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getDependencies(Path payload) throws ExecutionException {
        Path p = payload.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptyList();
        }

        try (InputStream in = Files.newInputStream(p)) {
            ObjectMapper om = new ObjectMapper();
            Map<String, Object> m = om.readValue(in, Map.class);

            Collection<String> deps = (Collection<String>) m.get(Constants.Request.DEPENDENCIES_KEY);
            return deps != null ? deps : Collections.emptyList();
        } catch (IOException e) {
            throw new ExecutionException("Error while reading a list of dependencies", e);
        }
    }

    private static boolean kill(Process proc) {
        if (!proc.isAlive()) {
            return false;
        }

        proc.destroy();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }

        while (proc.isAlive()) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                // ignore
            }
            proc.destroyForcibly();
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> getAgentParameters(Path payload) throws ExecutionException {
        Path p = payload.resolve(Constants.Agent.AGENT_PARAMS_FILE_NAME);
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
