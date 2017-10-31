package com.walmartlabs.concord.server.agent;

import com.walmartlabs.concord.rpc.CancelJobCommand;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.rpc.CommandQueueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private final ProcessQueueDao queueDao;
    private final CommandQueueImpl commandQueue;

    @Inject
    public AgentManager(ProcessQueueDao queueDao, CommandQueueImpl commandQueue) {
        this.queueDao = queueDao;
        this.commandQueue = commandQueue;
    }

    public void killProcess(UUID instanceId) {
        ProcessEntry e = queueDao.get(instanceId);
        if (e == null) {
            throw new IllegalArgumentException("Process not found: " + instanceId);
        }

        String agentId = e.getLastAgentId();
        if (agentId == null) {
            log.warn("killProcess ['{}'] -> trying to kill a process w/o an agent", instanceId);
            queueDao.update(instanceId, ProcessStatus.CANCELLED);
            return;
        }

        commandQueue.add(agentId, new CancelJobCommand(instanceId.toString()));
    }
}
