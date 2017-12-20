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

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class AgentCommand implements Serializable {

    private final UUID commandId;
    private final String agentId;
    private final Status status;
    private final Date createdAt;
    private final Map<String, Object> data;

    public AgentCommand(UUID commandId, String agentId, Status status, Date createdAt, Map<String, Object> data) {
        this.commandId = commandId;
        this.agentId = agentId;
        this.status = status;
        this.createdAt = createdAt;
        this.data = data;
    }

    public UUID getCommandId() {
        return commandId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Status getStatus() {
        return status;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public Map<String, Object> getData() {
        return data;
    }

    @Override
    public String toString() {
        return "AgentCommand{" +
                "commandId=" + commandId +
                ", agentId='" + agentId + '\'' +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", data=" + data +
                '}';
    }

    public enum Status {
        CREATED,
        SENT,
        FAILED
    }
}
