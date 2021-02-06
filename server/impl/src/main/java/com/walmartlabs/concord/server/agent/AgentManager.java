package com.walmartlabs.concord.server.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.server.process.ProcessEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Named
public class AgentManager {

    private static final Logger log = LoggerFactory.getLogger(AgentManager.class);

    private final ProcessQueueDao queueDao;
    private final AgentCommandsDao commandQueue;
    private final ProcessQueueManager queueManager;
    private final WebSocketChannelManager channelManager;

    @Inject
    public AgentManager(ProcessQueueDao queueDao,
                        AgentCommandsDao commandQueue,
                        ProcessQueueManager queueManager,
                        WebSocketChannelManager channelManager) {

        this.queueDao = queueDao;
        this.commandQueue = commandQueue;
        this.queueManager = queueManager;
        this.channelManager = channelManager;
    }

    public Collection<AgentWorkerEntry> getAvailableAgents() {
        Map<WebSocketChannel, ProcessRequest> reqs = channelManager.getRequests(MessageType.PROCESS_REQUEST);
        return reqs.entrySet().stream()
                .map(r -> AgentWorkerEntry.builder()
                        .channelId(r.getKey().getChannelId())
                        .agentId(r.getKey().getAgentId())
                        .userAgent(r.getKey().getUserAgent())
                        .capabilities(r.getValue().getCapabilities())
                        .build())
                .collect(Collectors.toList());
    }

    public void killProcess(ProcessKey processKey) {
        String agentId = queueDao.getLastAgentId(processKey);
        if (agentId == null) {
            log.warn("killProcess ['{}'] -> trying to kill a process w/o an agent", processKey);
            queueManager.updateStatus(processKey, ProcessStatus.CANCELLED);
            return;
        }

        killProcess(processKey, agentId);
    }

    public void killProcess(ProcessKey processKey, String agentId) {
        commandQueue.insert(UUID.randomUUID(), agentId, Commands.cancel(processKey));
    }

    public void killProcess(List<ProcessKey> processKeys) {
        if (processKeys.isEmpty()) {
            return;
        }

        // TODO replace with a more appropriate method
        List<ProcessEntry> l = queueDao.get(processKeys.stream()
                .map(k -> PartialProcessKey.from(k.getInstanceId()))
                .collect(Collectors.toList()));

        List<ProcessKey> withoutAgent = l.stream()
                .filter(p -> p.lastAgentId() == null)
                .map(p -> new ProcessKey(p.instanceId(), p.createdAt()))
                .collect(Collectors.toList());

        if (!withoutAgent.isEmpty()) {
            withoutAgent.forEach(p -> log.warn("killProcess ['{}'] -> trying to kill a process w/o an agent", p));
            queueManager.updateExpectedStatus(withoutAgent, null, ProcessStatus.CANCELLED);
        }

        List<AgentCommand> commands = l.stream()
                .filter(p -> p.lastAgentId() != null)
                .map(p -> new AgentCommand(UUID.randomUUID(), p.lastAgentId(), AgentCommand.Status.CREATED,
                        OffsetDateTime.now(), Commands.cancel(new ProcessKey(p.instanceId(), p.createdAt()))))
                .collect(Collectors.toList());

        commandQueue.insertBatch(commands);
    }
}
