package com.walmartlabs.concord.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.walmartlabs.concord.agent.ProcessPool.ProcessEntry;
import com.walmartlabs.concord.common.DependencyManager;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.Constants;
import com.walmartlabs.concord.rpc.AgentApiClient;
import com.walmartlabs.concord.rpc.ClientException;
import com.walmartlabs.concord.rpc.JobQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

public class DefaultJobExecutor implements JobExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultJobExecutor.class);

    private final Configuration cfg;
    private final LogManager logManager;
    private final DependencyManager dependencyManager;
    private final AgentApiClient client;
    private final ObjectMapper objectMapper;
    private final ProcessPool pool;
    private final ExecutorService executor;

    public DefaultJobExecutor(Configuration cfg, LogManager logManager,
                              DependencyManager dependencyManager, AgentApiClient client) {

        this.cfg = cfg;
        this.logManager = logManager;
        this.dependencyManager = dependencyManager;
        this.client = client;
        this.objectMapper = new ObjectMapper();
        this.pool = new ProcessPool(cfg.getMaxPreforkAge());
        this.executor = Executors.newCachedThreadPool();
    }

    @Override
    public JobInstance start(String instanceId, Path workDir, String entryPoint) throws ExecutionException {
        try {
            Collection<String> dependencies = getDependencyUrls(workDir);

            ProcessEntry entry;
            if (canUsePrefork(workDir)) {
                String[] cmd = createCommand(dependencies, workDir);
                entry = fork(instanceId, workDir, dependencies, cmd);
            } else {
                log.info("start ['{}'] -> can't use a pre-forked instance", instanceId);
                String[] cmd = createCommand(dependencies, workDir);
                entry = startOneTime(instanceId, workDir, dependencies, cmd);
            }

            Path payloadDir = entry.getWorkDir().resolve(Constants.Files.PAYLOAD_DIR_NAME);

            Process proc = entry.getProcess();
            CompletableFuture<?> f = CompletableFuture.supplyAsync(() -> exec(instanceId, entry.getWorkDir(), proc, payloadDir), executor);
            return createJobInstance(instanceId, proc, f);
        } catch (Exception e) {
            log.warn("start ['{}', '{}'] -> process startup error: {}", instanceId, workDir, e.getMessage());
            logManager.log(instanceId, "Process startup error: %s", e);

            CompletableFuture<?> f = new CompletableFuture<>();
            f.completeExceptionally(e);
            return createJobInstance(instanceId, null, f);
        }
    }

    private Integer exec(String instanceId, Path workDir, Process proc, Path payloadDir) {
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
                postProcess(instanceId, payloadDir);
            } catch (ExecutionException e) {
                log.warn("exec ['{}'] -> postprocessing error: {}", instanceId, e.getMessage());
                handleError(instanceId, workDir, proc, e.getMessage());
            }

            try {
                log.info("exec ['{}'] -> removing the working directory: {}", instanceId, workDir);
                IOUtils.deleteRecursively(workDir);
            } catch (IOException e) {
                log.warn("exec ['{}'] -> can't remove the working directory: {}", instanceId, e.getMessage());
            }
        }
    }

    private ProcessEntry fork(String instanceId, Path workDir, Collection<String> dependencies, String[] cmd) throws ExecutionException {
        HashCode hc = hash(cmd);

        ProcessEntry entry = pool.take(hc, () -> {
            Path forkDir = Files.createTempDirectory("prefork");
            return start(forkDir, dependencies, cmd);
        });

        try {
            Path payloadDir = entry.getWorkDir().resolve(Constants.Files.PAYLOAD_DIR_NAME);
            // TODO use move
            IOUtils.copy(workDir, payloadDir);
            writeInstanceId(instanceId, payloadDir);
        } catch (IOException e) {
            throw new ExecutionException("Error while starting a process", e);
        }

        return entry;
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

                if (proc != null && Utils.kill(proc)) {
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

    private ProcessEntry startOneTime(String instanceId, Path workDir, Collection<String> dependencies, String[] cmd) throws IOException {
        Path procDir = Files.createTempDirectory("onetime");

        Path payloadDir = procDir.resolve(Constants.Files.PAYLOAD_DIR_NAME);
        Files.move(workDir, payloadDir, StandardCopyOption.ATOMIC_MOVE);
        writeInstanceId(instanceId, payloadDir);

        return start(procDir, dependencies, cmd);
    }

    private ProcessEntry start(Path workDir, Collection<String> dependencies, String[] cmd) throws IOException {
        Path depsDir = workDir.resolve(Constants.Files.LIBRARIES_DIR_NAME);
        dependencyManager.collectDependencies(dependencies, depsDir);

        Path payloadDir = workDir.resolve(Constants.Files.PAYLOAD_DIR_NAME);
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
        env.put("_CONCORD_ATTACHMENTS_DIR", payloadDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME)
                .toAbsolutePath().toString());

        Process p = b.start();
        return new ProcessEntry(p, workDir);
    }

    private JobExecutorException handleError(String id, Path workDir, Process proc, String error) {
        log.warn("handleError ['{}', '{}'] -> execution error: {}", id, workDir, error);
        logManager.log(id, "Error: " + error);

        if (Utils.kill(proc)) {
            log.warn("handleError ['{}', '{}'] -> killed by agent", id, workDir);
        }

        throw new JobExecutorException("Error while executing a job: " + error);
    }

    private String[] createCommand(Collection<String> dependencies, Path workDir) {
        List<String> l = new ArrayList<>();

        l.add(cfg.getAgentJavaCmd());

        // JVM arguments

        List<String> agentParams = getAgentJvmParams(workDir);
        if (agentParams != null) {
            l.addAll(agentParams);
        } else {
            // default JVM parameters
            l.add("-noverify");
            l.add("-Xmx128m");
            l.add("-Djavax.el.varArgs=true");
            l.add("-Djava.security.egd=file:/dev/./urandom");
            l.add("-Djava.net.preferIPv4Stack=true");

            // workaround for JDK-8142508
            l.add("-Dsun.zip.disableMemoryMapping=true");
        }

        // Concord properties
        l.add("-DagentId=" + cfg.getAgentId());
        l.add("-Drpc.server.host=" + cfg.getServerHost());
        l.add("-Drpc.server.port=" + cfg.getServerPort());

        // classpath
        Collection<String> dependencyNames = dependencies.stream()
                .map(DefaultJobExecutor::getDependencyName)
                .collect(Collectors.toSet());

        l.add("-cp");

        // dependencies are stored in a '../lib/' directory relative to the working directory (payload)
        String deps = Utils.createClassPath("../" + Constants.Files.LIBRARIES_DIR_NAME + "/", dependencyNames);

        // payload's own libraries are stored in `./lib/` directory in the working directory
        String libs = Constants.Files.LIBRARIES_DIR_NAME + "/*";

        // the runner's runtime is stored somewhere in the agent's libraries
        String runner = cfg.getRunnerPath().toAbsolutePath().toString();

        l.add(joinClassPath(deps, libs, runner));

        // main class
        l.add("com.walmartlabs.concord.runner.Main");

        return l.toArray(new String[l.size()]);
    }

    private Collection<String> getDependencyUrls(Path workDir) throws ExecutionException {
        Path p = workDir.resolve(Constants.Files.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(p)) {
            return Collections.emptySet();
        }

        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            Collection<String> deps = (Collection<String>) m.get(Constants.Request.DEPENDENCIES_KEY);
            return normalizeUrls(deps != null ? new HashSet<>(deps) : Collections.emptySet());
        } catch (IOException e) {
            throw new ExecutionException("Error while reading a list of dependencies", e);
        }
    }

    private static Collection<String> normalizeUrls(Collection<String> urls) throws IOException {
        Collection<String> result = new HashSet<>();

        for (String s : urls) {
            if (s.endsWith(".jar")) {
                result.add(s);
                log.info("normalizeUrls -> using as is: {}", s);
                continue;
            }

            URL u = new URL(s);

            while (true) {
                String proto = u.getProtocol();
                if ("http".equalsIgnoreCase(proto) || "https".equalsIgnoreCase(proto)) {
                    URLConnection conn = u.openConnection();
                    if (conn instanceof HttpURLConnection) {
                        HttpURLConnection httpConn = (HttpURLConnection) conn;
                        httpConn.setInstanceFollowRedirects(false);

                        int code = httpConn.getResponseCode();
                        if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                                code == HttpURLConnection.HTTP_MOVED_PERM ||
                                code == HttpURLConnection.HTTP_SEE_OTHER ||
                                code == 307) {

                            String location = httpConn.getHeaderField("Location");
                            u = new URL(location);
                            log.info("normalizeUrls -> using: {}", location);

                            continue;
                        }

                        s = u.toString();
                    } else {
                        log.warn("normalizeUrls -> unexpected connection type: {} (for {})", conn.getClass(), s);
                    }
                }

                break;
            }

            result.add(s);
        }

        return result;
    }

    private void postProcess(String instanceId, Path payloadDir) throws ExecutionException {
        Path attachmentsDir = payloadDir.resolve(Constants.Files.JOB_ATTACHMENTS_DIR_NAME);
        if (!Files.exists(attachmentsDir)) {
            return;
        }

        JobQueue q = client.getJobQueue();

        // send attachments

        Path tmp;
        try {
            // TODO cfg
            tmp = Files.createTempFile("attachments", ".zip");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp))) {
                IOUtils.zip(zip, attachmentsDir);
            }
        } catch (IOException e) {
            throw new ExecutionException("Error while archiving the attachments: " + instanceId, e);
        }

        try {
            q.uploadAttachments(instanceId, tmp);
        } catch (ClientException e) {
            throw new ExecutionException("Error while sending the attachments: " + instanceId, e);
        }
    }

    private List<String> getAgentJvmParams(Path workDir) {
        Path p = workDir.resolve(Constants.Agent.AGENT_PARAMS_FILE_NAME);
        if (!Files.exists(p)) {
            return null;
        }

        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            return (List<String>) m.get(Constants.Agent.JVM_ARGS_KEY);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static boolean canUsePrefork(Path workDir) {
        if (Files.exists(workDir.resolve(Constants.Files.LIBRARIES_DIR_NAME))) {
            // payload supplied its own libraries
            return false;
        }

        if (Files.exists(workDir.resolve(Constants.Agent.AGENT_PARAMS_FILE_NAME))) {
            // payload supplied its own JVM parameters
            return false;
        }

        return true;
    }

    private static void writeInstanceId(String instanceId, Path dst) throws IOException {
        Path idPath = dst.resolve(Constants.Files.INSTANCE_ID_FILE_NAME);
        Files.write(idPath, instanceId.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.SYNC);
    }

    private static HashCode hash(String[] as) {
        HashFunction f = Hashing.sha256();
        Hasher h = f.newHasher();
        for (String s : as) {
            h.putString(s, Charsets.UTF_8);
        }
        return h.hash();
    }

    private static String getDependencyName(String s) {
        if (s == null) {
            return null;
        }

        int i = s.lastIndexOf("/");
        if (i >= 0 && i + 1 < s.length()) {
            return s.substring(i + 1);
        }

        return s;
    }

    private static String joinClassPath(String... as) {
        return String.join(":", Arrays.stream(as)
                .filter(s -> s != null && !s.trim().isEmpty())
                .collect(Collectors.toList()));
    }

    public static String getLastPart(URL url) {
        String p = url.getPath();
        int idx = p.lastIndexOf('/');
        if (idx >= 0 && idx + 1 < p.length()) {
            return p.substring(idx + 1);
        }
        throw new IllegalArgumentException("Invalid URL: " + url);
    }
}
