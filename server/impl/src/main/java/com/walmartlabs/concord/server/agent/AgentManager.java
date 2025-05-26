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

import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.jooq.DSLContext;

import javax.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

public class AgentManager {

    private final AgentCommandsDao commandQueue;
    private final WebSocketChannelManager channelManager;
    private final UuidGenerator uuidGenerator;

    @Inject
    public AgentManager(AgentCommandsDao commandQueue,
                        WebSocketChannelManager channelManager, UuidGenerator uuidGenerator) {

        this.commandQueue = requireNonNull(commandQueue);
        this.channelManager = requireNonNull(channelManager);
        this.uuidGenerator = requireNonNull(uuidGenerator);
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

    public void killProcess(ProcessKey processKey, String agentId) {
        commandQueue.tx(tx -> killProcess(tx, processKey, agentId));
    }

    public void killProcess(DSLContext tx, ProcessKey processKey, String agentId) {
        commandQueue.insert(tx, uuidGenerator.generate(), agentId, Commands.cancel(processKey));
    }

    public void killProcess(List<KeyAndAgent> processes) {
        List<AgentCommand> commands = processes.stream()
                .filter(p -> p.getAgentId() != null)
                .map(p -> new AgentCommand(uuidGenerator.generate(), p.getAgentId(), AgentCommand.Status.CREATED,
                        OffsetDateTime.now(), Commands.cancel(p.getProcessKey())))
                .collect(Collectors.toList());

        commandQueue.insertBatch(commands);
    }

    public static class KeyAndAgent {

        private final ProcessKey processKey;

        private final String agentId;

        public KeyAndAgent(ProcessKey processKey, String agentId) {
            this.processKey = processKey;
            this.agentId = agentId;
        }

        public ProcessKey getProcessKey() {
            return processKey;
        }

        public String getAgentId() {
            return agentId;
        }
    }
}
