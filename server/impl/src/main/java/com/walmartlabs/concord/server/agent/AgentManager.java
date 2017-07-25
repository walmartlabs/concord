package com.walmartlabs.concord.server.agent;

import com.walmartlabs.concord.rpc.CancelJobCommand;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.rpc.CommandQueueImpl;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.UUID;

@Named
public class AgentManager {

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
            // TODO throw an exception?
            return;
        }

        String agentId = e.getLastAgentId();
        if (agentId == null) {
            // TODO throw an exception?
            return;
        }

        commandQueue.add(agentId, new CancelJobCommand(instanceId.toString()));
    }
}
