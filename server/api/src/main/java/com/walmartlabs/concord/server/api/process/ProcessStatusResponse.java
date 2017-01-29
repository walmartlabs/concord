package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

public class ProcessStatusResponse implements Serializable {

    private final boolean ok = true;
    private final Date lastChangeDt;
    private final ProcessStatus status;
    private final String logFileName;

    @JsonCreator
    public ProcessStatusResponse(@JsonProperty("lastChangeDt") Date lastChangeDt,
                                 @JsonProperty("status") ProcessStatus status,
                                 @JsonProperty("logFileName") String logFileName) {
        this.lastChangeDt = lastChangeDt;
        this.status = status;
        this.logFileName = logFileName;
    }

    public Date getLastChangeDt() {
        return lastChangeDt;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public boolean isOk() {
        return ok;
    }

    @Override
    public String toString() {
        return "ProcessInstanceResponse{" +
                "ok=" + ok +
                ", lastChangeDt=" + lastChangeDt +
                ", status=" + status +
                ", logFileName='" + logFileName + '\'' +
                '}';
    }
}
