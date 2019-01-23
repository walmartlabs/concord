package com.walmartlabs.concord.server.process.queue;

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
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.process.ProcessManager;
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
import java.util.concurrent.TimeUnit;

@Named
@Singleton
public class ProcessQueueWebSocketHandler extends PeriodicTask {

    private static final long POLL_DELAY = TimeUnit.SECONDS.toMillis(1);
    private static final long ERROR_DELAY = TimeUnit.MINUTES.toMillis(1);

    private final WebSocketChannelManager channelManager;
    private final ProcessManager processManager;
    private final OrganizationDao organizationDao;
    private final RepositoryDao repositoryDao;
    private final LogManager logManager;

    @Inject
    public ProcessQueueWebSocketHandler(WebSocketChannelManager channelManager, ProcessManager processManager,
                                        OrganizationDao organizationDao, RepositoryDao repositoryDao, LogManager logManager) {
        super(POLL_DELAY, ERROR_DELAY);
        this.channelManager = channelManager;
        this.processManager = processManager;
        this.organizationDao = organizationDao;
        this.repositoryDao = repositoryDao;
        this.logManager = logManager;
    }

    @Override
    protected void performTask() {
        Map<WebSocketChannel, ProcessRequest> requests = this.channelManager.getRequests(MessageType.PROCESS_REQUEST);
        if (requests.isEmpty()) {
            return;
        }

        requests.forEach((channel, req) -> {
            ProcessQueueDao.ProcessItem item = processManager.nextProcess(req.getCapabilities());
            if (item == null) {
                return;
            }
            String orgName = null;
            String secret = null;
            if (item.getRepoId() != null) {
                RepositoryEntry repository = repositoryDao.get(item.getRepoId());
                if (repository != null) {
                    secret = repository.getSecretName();
                }
            }
            if (item.getOrgId() != null) {
                orgName = organizationDao.get(item.getOrgId()).getName();
            }

            channelManager.sendResponse(channel.getChannelId(),
                    new ProcessResponse(req.getCorrelationId(), item.getKey().getInstanceId(),
                            orgName, item.getRepoUrl(), item.getRepoPath(), item.getCommitId(), secret));
            logManager.info(item.getKey(), "Acquired by: " + channel.getInfo());
        });
    }
}
