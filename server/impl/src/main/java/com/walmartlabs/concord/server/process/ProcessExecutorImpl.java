package com.walmartlabs.concord.server.process;

//import com.walmartlabs.concord.agent.api.AgentResource;

import com.walmartlabs.concord.agent.api.JobType;
import com.walmartlabs.concord.agent.pool.AgentConnection;
import com.walmartlabs.concord.agent.pool.AgentPool;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.core.Response;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Named
public class ProcessExecutorImpl {

    private static final Logger log = LoggerFactory.getLogger(ProcessExecutorImpl.class);

    /**
     * Minimal period of time (in ms) between updating the status of a process on the server.
     */
    private static final long PROCESS_UPDATE_PERIOD = 5000;
    private static final int LOG_BUFFER_SIZE = 256;

    private final AgentPool agentPool;

    @Inject
    public ProcessExecutorImpl(AgentPool agentPool) {
        this.agentPool = agentPool;
    }

    public void run(String instanceId, Path archive, String entryPoint, Path logFile, ProcessExecutorCallback callback) {

        try {
            _run(instanceId, archive, entryPoint, logFile, callback);
        } catch (Exception e) {
            log.error("run ['{}'] -> process error", instanceId, e);
            callback.onStatusChange(instanceId, ProcessStatus.FAILED);
            log(logFile, "Failed: %s", e.getMessage());
            throw e;
        }
    }

    private void _run(String instanceId, Path archive, String entryPoint, Path logFile, ProcessExecutorCallback callback) {
        log.info("run ['{}'] -> starting (log file: {})...", instanceId, logFile);
        log(logFile, "Starting %s...", instanceId);

        try (AgentConnection a = agentPool.getConnection()) {
            log.debug("run ['{}'] -> sending the payload: {}", instanceId, archive.toAbsolutePath());
            callback.onStatusChange(instanceId, ProcessStatus.RUNNING);

            try (InputStream in = new BufferedInputStream(Files.newInputStream(archive))) {
                a.start(instanceId, JobType.JAR, entryPoint, in);

                log.debug("run ['{}'] -> started", instanceId);
            } catch (Exception e) {
                // TODO stacktrace
                log.warn("run ['{}'] -> failed: {}", instanceId, e.getMessage());
                log(logFile, "Error while starting a process: %s", e.getMessage());
                throw new ProcessException("Error while starting a process", e);
            }

            // stream back the job's log

            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(logFile, StandardOpenOption.APPEND))) {
                stream(a, instanceId, out, () -> callback.onUpdate(instanceId));
            } catch (IOException e) {
                throw new ProcessException("Error while streaming an agent's log", e);
            }

            log.info("run ['{}'] -> done", instanceId);
            log(logFile, "...done");
        }
    }

    public void cancel(String instanceId) {
        try (AgentConnection a = agentPool.getConnection()) {
            a.cancel(instanceId, true);
        }
    }

    private void stream(AgentConnection a, String jobId, OutputStream out, Runnable progressUpdater) throws IOException {
        progressUpdater = new ThrottledRunnable(progressUpdater, PROCESS_UPDATE_PERIOD);

        Response r = a.streamLog(jobId);
        try (InputStream in = r.readEntity(InputStream.class)) {
            byte[] ab = new byte[LOG_BUFFER_SIZE];
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

    private static void log(Path p, String s, Object... args) {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(p, StandardOpenOption.APPEND))) {
            out.write(String.format(s, args).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        } catch (IOException e) {
            log.warn("log ['{}'] -> error writing to a log file", p, e);
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
