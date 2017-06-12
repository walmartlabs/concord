package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.project.UpdateRepositoryRequest;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessEventEntry implements Serializable {

    private final String processInstanceId;

    private final int eventType;

    private final Date eventDate;

    private final String data;

    @JsonCreator
    public ProcessEventEntry(@JsonProperty("processInstanceId") String processInstanceId,
                             @JsonProperty("eventType") int eventType,
                             @JsonProperty("eventDate") Date eventDate,
                             @JsonProperty("data") String data) {

        this.processInstanceId = processInstanceId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.data = data;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public int getEventType() {
        return eventType;
    }

    public Date getEventDate() {
        return eventDate;
    }

    public String getData() {
        return data;
    }
}
