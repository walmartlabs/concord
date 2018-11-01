package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.queueclient.message.MessageType;
import com.walmartlabs.concord.server.queueclient.message.ProcessRequest;
import com.walmartlabs.concord.server.queueclient.message.ProcessResponse;
import com.walmartlabs.concord.server.websocket.WebSocketChannel;
import com.walmartlabs.concord.server.websocket.WebSocketChannelManager;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;

@Named
@Singleton
public class ProcessWebSocketHandler extends PeriodicTask {

    private static final long POLL_DELAY = 1000; // 1 sec
    private static final long ERROR_DELAY = 1 * 60 * 1000; // 1 min

    private final WebSocketChannelManager channelManager;
    private final ProcessManager processManager;
    private final LogManager logManager;

    @Inject
    public ProcessWebSocketHandler(WebSocketChannelManager channelManager, ProcessManager processManager, LogManager logManager) {
        super(POLL_DELAY, ERROR_DELAY);
        this.channelManager = channelManager;
        this.processManager = processManager;
        this.logManager = logManager;
    }

    @Override
    protected void performTask() {
        Map<WebSocketChannel, ProcessRequest> requests = this.channelManager.getRequests(MessageType.PROCESS_REQUEST);
        if (requests.isEmpty()) {
            return;
        }

        requests.forEach((channel, req) -> {
            ProcessKey processKey = processManager.nextPayload(req.getCapabilities());
            if (processKey != null) {
                channelManager.sendResponse(channel.getChannelId(), new ProcessResponse(req.getCorrelationId(), processKey.getInstanceId()));
                logManager.info(processKey, "Acquired by: " + channel.getInfo());
            }
        });
    }
}
