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

import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

@Named
public class AgentManager {

    private static final List<ProcessStatus> CANCELLABLE_STATUSES = Arrays.asList(
            ProcessStatus.NEW,
            ProcessStatus.PREPARING,
            ProcessStatus.ENQUEUED,
            ProcessStatus.STARTING,
            ProcessStatus.RUNNING,
            ProcessStatus.SUSPENDED,
            ProcessStatus.RESUMING);

    private final ProcessQueueManager queueManager;
    private final WebSocketChannelManager channelManager;

    @Inject
    public AgentManager(ProcessQueueManager queueManager,
                        WebSocketChannelManager channelManager) {

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
        queueManager.updateExpectedStatus(Collections.singletonList(processKey), CANCELLABLE_STATUSES, ProcessStatus.CANCELLED);
    }

    public void killProcess(List<ProcessKey> processKeys) {
        if (processKeys.isEmpty()) {
            return;
        }

        queueManager.updateExpectedStatus(processKeys, CANCELLABLE_STATUSES, ProcessStatus.CANCELLED);
    }
}
