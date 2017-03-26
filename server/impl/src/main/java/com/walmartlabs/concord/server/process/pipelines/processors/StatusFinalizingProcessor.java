package com.walmartlabs.concord.server.process.pipelines.processors;

import com.walmartlabs.concord.agent.api.JobStatus;
import com.walmartlabs.concord.agent.pool.AgentConnection;
import com.walmartlabs.concord.agent.pool.AgentPool;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.history.ProcessHistoryDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessAttachmentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class StatusFinalizingProcessor implements PayloadProcessor {

    private static final Logger log = LoggerFactory.getLogger(StatusFinalizingProcessor.class);

    private final AgentPool agentPool;
    private final ProcessHistoryDao historyDao;
    private final ProcessAttachmentManager attachmentManager;

    @Inject
    public StatusFinalizingProcessor(AgentPool agentPool,
                                     ProcessHistoryDao historyDao,
                                     ProcessAttachmentManager attachmentManager) {

        this.agentPool = agentPool;
        this.historyDao = historyDao;
        this.attachmentManager = attachmentManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        String instanceId = payload.getInstanceId();

        JobStatus s;
        try (AgentConnection a = agentPool.getConnection()) {
            s = a.getStatus(instanceId);
        }

        if (s == JobStatus.RUNNING) {
            log.warn("run ['{}'] -> job is still running", instanceId);
        } else if (s == JobStatus.FAILED || s == JobStatus.CANCELLED) {
            historyDao.update(instanceId, ProcessStatus.FAILED);
        } else {
            boolean suspended = isSuspended(instanceId);
            historyDao.update(instanceId, suspended ? ProcessStatus.SUSPENDED : ProcessStatus.FINISHED);
        }

        return chain.process(payload);
    }

    private boolean isSuspended(String instanceId) {
        String resource = Constants.JOB_STATE_DIR_NAME + "/" + Constants.SUSPEND_MARKER_FILE_NAME;
        return attachmentManager.contains(instanceId, resource);
    }
}
