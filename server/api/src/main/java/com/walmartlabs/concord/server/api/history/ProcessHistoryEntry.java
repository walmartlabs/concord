package com.walmartlabs.concord.server.api.history;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.api.process.ProcessStatus;

import java.io.Serializable;
import java.util.Date;

@JsonInclude(Include.NON_NULL)
public class ProcessHistoryEntry implements Serializable {

    private final String instanceId;
    private final String projectName;
    private final Date createdDt;
    private final String initiator;
    private final ProcessStatus status;
    private final Date lastUpdateDt;
    private final String logFileName;

    public ProcessHistoryEntry(String instanceId, String projectName, Date createdDt,
                               String initiator, ProcessStatus status, Date lastUpdateDt, String logFileName) {

        this.instanceId = instanceId;
        this.projectName = projectName;
        this.createdDt = createdDt;
        this.initiator = initiator;
        this.status = status;
        this.lastUpdateDt = lastUpdateDt;
        this.logFileName = logFileName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getProjectName() {
        return projectName;
    }

    public Date getCreatedDt() {
        return createdDt;
    }

    public String getInitiator() {
        return initiator;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public Date getlastUpdateDt() {
        return lastUpdateDt;
    }

    public String getLogFileName() {
        return logFileName;
    }

    @JsonIgnore
    @JsonProperty
    public boolean isOk() {
        return true;
    }

    @Override
    public String toString() {
        return "ProcessHistoryEntry{" +
                "instanceId='" + instanceId + '\'' +
                ", projectName='" + projectName + '\'' +
                ", createdDt=" + createdDt +
                ", initiator='" + initiator + '\'' +
                ", status=" + status +
                ", lastUpdateDt=" + lastUpdateDt +
                ", logFileName='" + logFileName + '\'' +
                '}';
    }
}
