package com.walmartlabs.concord.server.process;

import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static com.walmartlabs.concord.server.process.state.ProcessStateManager.path;
import static com.walmartlabs.concord.server.process.state.ProcessStateManager.zipTo;

@Named
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

    private final ProcessQueueDao queueDao;
    private final ProcessStateManager stateManager;
    private final LogManager logManager;

    @Inject
    public ProcessManager(ProcessQueueDao queueDao, ProcessStateManager stateManager, LogManager logManager) {
        this.queueDao = queueDao;
        this.stateManager = stateManager;
        this.logManager = logManager;
    }

    public PayloadEntry nextPayload() throws IOException {
        ProcessEntry p = queueDao.poll();
        if (p == null) {
            return null;
        }

        UUID instanceId = p.getInstanceId();

        // TODO this probably can be replaced with an in-memory buffer
        Path tmp = Files.createTempFile("payload", ".zip");
        try (ZipArchiveOutputStream zip = new ZipArchiveOutputStream(Files.newOutputStream(tmp))) {
            stateManager.export(instanceId, zipTo(zip));
        }

        return new PayloadEntry(p, tmp);
    }

    public void updateStatus(UUID instanceId, String agentId, ProcessStatus status) {
        if (status == ProcessStatus.FINISHED && isSuspended(instanceId)) {
            status = ProcessStatus.SUSPENDED;
        }

        queueDao.updateAgentId(instanceId, agentId, status);
        logManager.info(instanceId, "Process status: {}", status);

        log.info("updateStatus [{}, '{}', {}] -> done", instanceId, agentId, status);
    }

    private boolean isSuspended(UUID instanceId) {
        String resource = path(InternalConstants.Files.JOB_ATTACHMENTS_DIR_NAME,
                InternalConstants.Files.JOB_STATE_DIR_NAME,
                InternalConstants.Files.SUSPEND_MARKER_FILE_NAME);

        return stateManager.exists(instanceId, resource);
    }

    public static class PayloadEntry {

        private final ProcessEntry processEntry;
        private final Path payloadArchive;

        public PayloadEntry(ProcessEntry processEntry, Path payloadArchive) {
            this.processEntry = processEntry;
            this.payloadArchive = payloadArchive;
        }

        public ProcessEntry getProcessEntry() {
            return processEntry;
        }

        public Path getPayloadArchive() {
            return payloadArchive;
        }
    }
}
