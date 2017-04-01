package com.walmartlabs.concord.plugins.nexus.perf2;

import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.common.Task;
import io.takari.bpm.api.BpmnError;
import io.takari.bpm.api.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.zip.ZipOutputStream;

@Named
@Singleton
public class PerfTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(PerfTask.class);
    private static final String[] SKIP_FILES = {"\\.git"};
    private static final String SESSION_ID_KEY = "__perftask_session_id";

    private final AgentPoolFactory agentPoolFactory;
    private final Map<UUID, PerfSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public PerfTask(AgentPoolFactory agentPoolFactory) {
        this.agentPoolFactory = agentPoolFactory;
    }

    @Override
    public String getKey() {
        return "perf2";
    }

    public void loadAndStart(ExecutionContext ctx, String agentAddr, String scenarioDir, Collection<Map<String, Object>> scenarios) {
        Path srcDir = getPath(ctx, Constants.LOCAL_PATH_KEY);

        PerfSession session = null;
        try {
            File archiveFile = File.createTempFile("perf", ".zip");
            log.info("Creating an archive: {}", archiveFile);

            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(archiveFile));
                 ZipOutputStream zip = new ZipOutputStream(os)) {

                // pack dependencies
                Path libPath = srcDir.resolve(Constants.LIBRARIES_DIR_NAME);
                File libDir = libPath.toFile();
                if (libDir.exists() && libDir.isDirectory()) {
                    IOUtils.zip(zip, libPath, SKIP_FILES);
                }

                // pack scenarios
                // TODO validate
                Path scsPath = srcDir.resolve(scenarioDir);
                File scsDir = scsPath.toFile();
                if (!scsDir.exists() || !scsDir.isDirectory()) {
                    throw new BpmnError("Directory not found: " + scenarios);
                }
                IOUtils.zip(zip, scsPath, SKIP_FILES);
            }

            // TODO cfg
            session = PerfSession.create(agentPoolFactory, resolveHost(agentAddr, 8002));
            session.loadAndStart(archiveFile, scenarios);

            UUID sessionId = session.getId();

            sessions.put(sessionId, session);
            ctx.setVariable(SESSION_ID_KEY, sessionId);
        } catch (BpmnError e) {
            if (session != null) {
                session.close();
            }
            throw e;
        } catch (Exception e) {
            if (session != null) {
                session.close();
            }
            throw new BpmnError("perfSessionError", e);
        }
    }

    public void close(ExecutionContext ctx) {
        PerfSession session = getSession(ctx);
        try {
            session.close();
        } finally {
            sessions.remove(session.getId());
        }
    }

    public void waitToFinish(ExecutionContext ctx, long timeout) {
        PerfSession session = getSession(ctx);
        try {
            session.waitToFinish(timeout);
        } catch (TimeoutException e) {
            throw new BpmnError("perfSessionTimeout", e);
        }
    }

    private PerfSession getSession(ExecutionContext ctx) {
        UUID sId = (UUID) ctx.getVariable(SESSION_ID_KEY);
        if (sId == null) {
            throw new BpmnError("perfSessionNotFound");
        }

        PerfSession s = sessions.get(sId);
        if (s == null) {
            throw new BpmnError("perfSessionNotFound");
        }

        return s;
    }

    private static Path getPath(ExecutionContext ctx, String k) {
        Object v = ctx.getVariable(k);

        if (v == null) {
            throw new IllegalArgumentException("Expected a value: " + k);
        }

        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Expected a string: " + k);
        }

        return new File((String) v).toPath();
    }

    private Collection<URI> resolveHost(String host, int port) {
        try {
            InetAddress[] as = InetAddress.getAllByName(host);
            Collection<URI> result = new ArrayList<>(as.length);
            for (InetAddress a : as) {
                // TODO constants
                result.add(URI.create("http://" + a.getHostAddress() + ":" + port));
            }
            return result;
        } catch (Exception e) {
            log.error("Error while resolving an agent's host: {}", host, e);
            throw new BpmnError("resolveHostError", e);
        }
    }
}
