package com.walmartlabs.concord.server.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import com.walmartlabs.concord.rpc.CancelJobCommand;
import com.walmartlabs.concord.rpc.Commands;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.rpc.CommandQueueImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    public void killProcess(List<UUID> instanceIdList) {
        List<ProcessEntry> l = queueDao.get(instanceIdList);

        List<UUID> withoutAgent = l.stream()
                .filter(p -> p.getLastAgentId() == null)
                .map(ProcessEntry::getInstanceId)
                .collect(Collectors.toList());

        if (!withoutAgent.isEmpty()) {
            withoutAgent.forEach(p -> log.warn("killProcess ['{}'] -> trying to kill a process w/o an agent", p));
            queueDao.update(withoutAgent, ProcessStatus.CANCELLED, null);
        }

        List<AgentCommand> commands = l.stream()
                .filter(p -> p.getLastAgentId() != null)
                .map(p -> new AgentCommand(UUID.randomUUID(), p.getLastAgentId(), AgentCommand.Status.CREATED,
                        new Date(), Commands.toMap(new CancelJobCommand(p.getInstanceId().toString()))))
                .collect(Collectors.toList());

        commandQueue.addBatch(commands);
    }
}
