package com.walmartlabs.concord.agent;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.server.ApiException;
import com.walmartlabs.concord.server.api.CommandType;
import com.walmartlabs.concord.server.api.agent.CommandEntry;
import com.walmartlabs.concord.server.client.CommandQueueApi;

import java.io.IOException;

public class CommandQueueClient extends AbstractQueueClient<CommandEntry> {

    private final CommandQueueApi queueClient;

    private final String agentId;

    public CommandQueueClient(Configuration cfg) throws IOException {
        super(cfg);

        this.queueClient = new CommandQueueApi(getClient());
        this.agentId = cfg.getAgentId();
    }

    @Override
    protected CommandEntry poll() throws ApiException {
        return withRetry(() -> {
            com.walmartlabs.concord.server.client.CommandEntry cmd = queueClient.take(agentId);
            if (cmd != null) {
                return new CommandEntry(CommandType.valueOf(cmd.getType().getValue()), cmd.getPayload());
            }
            return null;
        });
    }
}
