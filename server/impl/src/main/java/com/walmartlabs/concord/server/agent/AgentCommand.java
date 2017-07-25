package com.walmartlabs.concord.server.agent;

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
        SENT
    }
}
