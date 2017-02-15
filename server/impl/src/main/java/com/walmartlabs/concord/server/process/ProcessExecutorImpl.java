package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.agent.api.JobResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.api.LogResource;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.cfg.RunnerConfiguration;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipOutputStream;

@Named
@Singleton
public class ProcessExecutorImpl {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutorImpl.class);

    /**
     * Minimal period of time (in ms) between updating the status of a process on the server.
     */
    private static final long PROCESS_UPDATE_PERIOD = 5000;

    private final AgentConfiguration agentCfg;

    private final Map<String, String> instanceIdMap = new ConcurrentHashMap<>();

    @Inject
    public ProcessExecutorImpl(AgentConfiguration agentCfg) {
        this.agentCfg = agentCfg;
    }

    public void run(String instanceId, Path archive, String entryPoint, Path logFile,
                    ProcessExecutorCallback callback) {

        try {
            _run(instanceId, archive, entryPoint, logFile, callback);
        } catch (Exception e) {
            log.error("run ['{}'] -> process error", instanceId, e);
            callback.onStatusChange(instanceId, ProcessStatus.FAILED);
            throw e;
        }
    }

    private void _run(String instanceId, Path archive, String entryPoint, Path logFile,
                      ProcessExecutorCallback callback) {

        log.info("run ['{}'] -> starting (log file: {})...", instanceId, logFile.toAbsolutePath());
        log(logFile, "Starting %s...", instanceId);

        try (Agent a = new Agent(agentCfg.getUri())) {
            log.debug("run ['{}'] -> sending the payload: {}", instanceId, archive.toAbsolutePath());
            callback.onStatusChange(instanceId, ProcessStatus.RUNNING);

            String jobId;
            try (InputStream in = new BufferedInputStream(Files.newInputStream(archive))) {
                jobId = a.jobResource.start(in, JobType.JAR, entryPoint);
                instanceIdMap.put(instanceId, jobId);

                log.debug("run ['{}'] -> started", instanceId);
                log(logFile, "Payload sent");
            } catch (Exception e) {
                // TODO stacktrace
                log.warn("run ['{}'] -> failed: {}", instanceId, e.getMessage());
                log(logFile, "Error while starting a process: %s", e.getMessage());
                throw new ProcessException("Error while starting a process", e);
            }

            // stream back the job's log

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
                a.stream(jobId, out, () -> callback.onUpdate(instanceId));
            } catch (IOException e) {
                throw new ProcessException("Error while streaming an agent's log", e);
            }

            log.info("run ['{}'] -> done", instanceId);
            log(logFile, "...done");

            JobStatus s = a.jobResource.getStatus(jobId);
            if (s == JobStatus.FAILED || s == JobStatus.CANCELLED) {
                callback.onStatusChange(instanceId, ProcessStatus.FAILED);
            } else if (s == JobStatus.RUNNING) {
                log.warn("run ['{}'] -> job is still running: {}", instanceId, jobId);
            } else {
                callback.onStatusChange(instanceId, ProcessStatus.FINISHED);
            }

            instanceIdMap.remove(instanceId);
        }
    }

    public void cancel(String instanceId) {
        String jobId = instanceIdMap.get(instanceId);
        if (jobId == null) {
            return;
        }

        try (Agent a = new Agent(agentCfg.getUri())) {
            a.jobResource.cancel(jobId, true);
        }
    }

    private static void addRunner(Path src, RunnerConfiguration cfg) {
        Path runnerPath = cfg.getPath();
        Path dst = src.resolve(cfg.getTargetName());
        try {
            Files.copy(runnerPath, dst);
        } catch (IOException e) {
            throw new ProcessException("Error while adding the runner's runtime", e);
        }
    }

    private static Path pack(Path src) {
        try {
            // TODO cfg?
            Path dst = Files.createTempFile("payload", ".zip");
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dst.toFile())))) {
                IOUtils.zip(out, src);
            }
            return dst;
        } catch (IOException e) {
            throw new ProcessException("Error while processing a payload", e);
        }
    }

    private static File createLogFile(Path p, String logFileName) {
        File dir = p.toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ProcessException("Can't create the log directory: " + dir.getAbsolutePath());
        }
        return new File(dir, logFileName);
    }

    private static void log(Path p, String s, Object... args) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(p, StandardOpenOption.APPEND))) {
            out.write(String.format(s, args).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        } catch (IOException e) {
            log.warn("log ['{}'] -> error writing to a log file", p, e);
        }
    }

    private static class Agent implements AutoCloseable {

        private final Client client;
        private final JobResource jobResource;
        private final LogResource logResource;

        private Agent(URI uri) {
            this.client = ClientBuilder.newClient();

            WebTarget t = client.target(uri);
            this.jobResource = ((ResteasyWebTarget) t).proxy(JobResource.class);
            this.logResource = ((ResteasyWebTarget) t).proxy(LogResource.class);
        }

        public void stream(String jobIb, OutputStream out, Runnable progressUpdater) throws IOException {
            // TODO constants
            progressUpdater = new ThrottledRunnable(progressUpdater, PROCESS_UPDATE_PERIOD);

            Response r = logResource.stream(jobIb);
            try (InputStream in = r.readEntity(InputStream.class)) {
                byte[] ab = new byte[256];
                int read;
                while ((read = in.read(ab)) > 0) {
                    out.write(ab, 0, read);
                    out.flush();
                    progressUpdater.run();
                }
            } finally {
                if (r != null) {
                    r.close();
                }
            }
        }

        @Override
        public void close() {
            client.close();
        }
    }

    private static final class ThrottledRunnable implements Runnable {

        private final Runnable delegate;
        private final long period;

        private long t1 = System.currentTimeMillis();

        private ThrottledRunnable(Runnable delegate, long period) {
            this.delegate = delegate;
            this.period = period;
        }

        @Override
        public void run() {
            long t2 = System.currentTimeMillis();
            if (t2 - t1 >= period) {
                t1 = t2;
                delegate.run();
            }
        }
    }
}
