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

import com.walmartlabs.concord.server.CommandType;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.queueclient.message.CommandRequest;
import com.walmartlabs.concord.server.queueclient.message.CommandResponse;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Named
@Singleton
public class AgentCommandWebSocketHandler extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(AgentCommandWebSocketHandler.class);

    private static final long POLL_DELAY = 1000; // 1 sec
    private static final long ERROR_DELAY = 1 * 60 * 1000; // 1 min

    private final WebSocketChannelManager channelManager;
    private final AgentCommandsDao dao;

    @Inject
    public AgentCommandWebSocketHandler(WebSocketChannelManager channelManager, AgentCommandsDao dao) {
        super(POLL_DELAY, ERROR_DELAY);
        this.channelManager = channelManager;
        this.dao = dao;
    }

    @Override
    protected void performTask() {
        Map<WebSocketChannel, CommandRequest> requests = this.channelManager.getRequests(MessageType.COMMAND_REQUEST);

        if (requests.isEmpty()) {
            return;
        }

        // currently it assumes that all commands are CANCEL_JOB commands
        requests.forEach((channel, req) -> dao.poll(req.getAgentId().toString()).ifPresent(cmd -> {
            CommandType type = CommandType.valueOf((String) cmd.getData().remove(Commands.TYPE_KEY));

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type.toString());
            payload.putAll(cmd.getData());

            boolean success = channelManager.sendResponse(channel.getChannelId(), CommandResponse.cancel(req.getCorrelationId(), payload));
            if (!success) {
                log.error("response ['{}'] -> send error", cmd);
            }
        }));
    }
}
