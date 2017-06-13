package com.walmartlabs.concord.server.api.process;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;

import java.io.Serializable;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessEventEntry implements Serializable {

    private final String processInstanceId;

    private final ProcessEventType eventType;

    private final Date eventDate;

    private final Object data;

    @JsonCreator
    public ProcessEventEntry(@JsonProperty("processInstanceId") String processInstanceId,
                             @JsonProperty("eventType") ProcessEventType eventType,
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

    public ProcessEventType getEventType() {
        return eventType;
    }

    public Date getEventDate() {
        return eventDate;
    }

    @JsonRawValue
    public Object getData() {
        return data;
    }
}
