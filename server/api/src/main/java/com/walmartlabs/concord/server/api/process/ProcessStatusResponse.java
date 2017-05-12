package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

@JsonInclude(Include.NON_NULL)
public class ProcessStatusResponse implements Serializable {

    private final boolean ok = true;
    private final String projectName;
    private final Date createdDt;
    private final String initiator;
    private final ProcessStatus status;
    private final Date lastUpdateDt;
    private final String logFileName;

    @JsonCreator
    public ProcessStatusResponse(@JsonProperty("projectName") String projectName,
                                 @JsonProperty("createdDt") Date createdDt,
                                 @JsonProperty("initiator") String initiator,
                                 @JsonProperty("lastUpdateDt") Date lastUpdateDt,
                                 @JsonProperty("status") ProcessStatus status,
                                 @JsonProperty("logFileName") String logFileName) {

        this.projectName = projectName;
        this.createdDt = createdDt;
        this.initiator = initiator;
        this.lastUpdateDt = lastUpdateDt;
        this.status = status;
        this.logFileName = logFileName;
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

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "ProcessStatusResponse{" +
                "ok=" + ok +
                ", projectName='" + projectName + '\'' +
                ", createdDt=" + createdDt +
                ", initiator='" + initiator + '\'' +
                ", status=" + status +
                ", lastUpdateDt=" + lastUpdateDt +
                ", logFileName='" + logFileName + '\'' +
                '}';
    }
}
