package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.agent.api.JobResource;
import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.api.LogResource;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.cfg.AgentConfiguration;
import com.walmartlabs.concord.server.cfg.LogStoreConfiguration;
import com.walmartlabs.concord.server.cfg.RunnerConfiguration;
import com.walmartlabs.concord.server.inventory.InventoryPayloadProcessor;
import com.walmartlabs.concord.server.template.TemplateEngine;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipOutputStream;

@Named
@Singleton
public class ProcessExecutorImpl implements ProcessExecutor {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutorImpl.class);

    private final LogStoreConfiguration logCfg;
    private final AgentConfiguration agentCfg;
    private final RunnerConfiguration runnerCfg;
    private final TemplateEngine templateEngine;
    private final InventoryPayloadProcessor inventoryProcessor;

    private final Map<String, String> instanceIdMap = new ConcurrentHashMap<>();

    @Inject
    public ProcessExecutorImpl(LogStoreConfiguration logCfg, AgentConfiguration agentCfg, RunnerConfiguration runnerCfg,
                               TemplateEngine templateEngine, InventoryPayloadProcessor inventoryProcessor) {

        this.logCfg = logCfg;
        this.agentCfg = agentCfg;
        this.runnerCfg = runnerCfg;
        this.templateEngine = templateEngine;
        this.inventoryProcessor = inventoryProcessor;
    }

    @Override
    public void run(Payload payload, ProcessExecutorCallback callback) throws ProcessExecutorException {
        try {
            _run(payload, callback);
        } catch (Exception e) {
            log.error("run ['{}'] -> process error", payload.getInstanceId(), e);
            callback.onStatusChange(payload, ProcessStatus.FAILED);
            throw e;
        }
    }

    public void _run(Payload payload, ProcessExecutorCallback callback) throws ProcessExecutorException {
        // repack the payload with the runner's jar

        addRunner(payload.getData(), runnerCfg);

        // run payload processors
        // TODO extract a common interface
        templateEngine.process(payload);
        inventoryProcessor.process(payload);

        Path data = pack(payload.getData());

        // send the payload to an agent

        String instanceId = payload.getInstanceId();

        File logFile = createLogFile(logCfg.getBaseDir(), payload.getLogFileName());
        log.info("run ['{}'] -> starting (log file: {})...", instanceId, logFile.getAbsolutePath());
        log(logFile, "Starting %s...", instanceId);

        try (Agent a = new Agent(agentCfg.getUri())) {
            log.info("run ['{}'] -> sending the payload: {}", instanceId, data.toAbsolutePath());
            callback.onStatusChange(payload, ProcessStatus.RUNNING);

            String jobId;
            try (InputStream in = new BufferedInputStream(new FileInputStream(data.toFile()))) {
                jobId = a.jobResource.start(in, JobType.JAR, "runner.jar");
                instanceIdMap.put(instanceId, jobId);

                log.info("run ['{}'] -> started", instanceId);
                log(logFile, "Payload sent");
            } catch (Exception e) {
                // TODO stacktrace
                log(logFile, "Error while starting a process: %s", e.getMessage());
                throw new ProcessExecutorException("Error while starting a process", e);
            }

            // stream back the job's log

            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(logFile, true))) {
                a.stream(jobId, out);
            } catch (IOException e) {
                throw new ProcessExecutorException("Error while streaming an agent's log", e);
            }

            log(logFile, "...done");

            JobStatus s = a.jobResource.getStatus(jobId);
            if (s == JobStatus.FAILED || s == JobStatus.CANCELLED) {
                callback.onStatusChange(payload, ProcessStatus.FAILED);
            } else {
                callback.onStatusChange(payload, ProcessStatus.FINISHED);
            }

            instanceIdMap.remove(instanceId);
        }
    }

    @Override
    public void cancel(String instanceId) {
        String jobId = instanceIdMap.get(instanceId);
        if (jobId == null) {
            return;
        }

        try (Agent a = new Agent(agentCfg.getUri())) {
            a.jobResource.cancel(jobId, false);
        }
    }

    private static void addRunner(Path src, RunnerConfiguration cfg) throws ProcessExecutorException {
        Path runnerPath = cfg.getPath();
        Path dst = src.resolve(cfg.getTargetName());
        try {
            Files.copy(runnerPath, dst);
        } catch (IOException e) {
            throw new ProcessExecutorException("Error while adding the runner's runtime", e);
        }
    }

    private static Path pack(Path src) throws ProcessExecutorException {
        try {
            // TODO cfg?
            Path dst = Files.createTempFile("payload", ".zip");
            try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(dst.toFile())))) {
                IOUtils.zip(out, src);
            }
            return dst;
        } catch (IOException e) {
            throw new ProcessExecutorException("Error while processing a payload", e);
        }
    }

    private static File createLogFile(Path p, String logFileName) throws ProcessExecutorException {
        File dir = p.toFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ProcessExecutorException("Can't create the log directory: " + dir.getAbsolutePath());
        }
        return new File(dir, logFileName);
    }

    private static void log(File f, String s, Object... args) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f, true))) {
            // TODO force utf-8?
            out.write(String.format(s, args).getBytes());
            out.write('\n');
        } catch (IOException e) {
            log.warn("log ['{}'] -> error writing to a log file", f, e);
        }
    }

    private static class Agent implements AutoCloseable {

        private final URI uri;
        private final Client client;
        private final JobResource jobResource;
        private final LogResource logResource;

        private Agent(URI uri) {
            this.uri = uri;
            this.client = ClientBuilder.newClient();

            WebTarget t = client.target(uri);
            this.jobResource = ((ResteasyWebTarget) t).proxy(JobResource.class);
            this.logResource = ((ResteasyWebTarget) t).proxy(LogResource.class);
        }

        public void stream(String jobIb, OutputStream out) throws IOException {
            Response r = logResource.stream(jobIb);
            try (InputStream in = r.readEntity(InputStream.class)) {
                byte[] ab = new byte[256];
                int read;
                while ((read = in.read(ab)) > 0) {
                    out.write(ab, 0, read);
                    out.flush();
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
}
