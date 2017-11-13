package com.walmartlabs.concord.server.api.trigger;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TriggerEntry implements Serializable {

    private final UUID id;

    private final UUID repositoryId;

    @ConcordKey
    private final String repositoryName;

    private final UUID projectId;

    @ConcordKey
    private final String projectName;

    @NotNull
    @ConcordKey
    private final String eventSource;

    @NotNull
    private final String entryPoint;

    private final Map<String, Object> arguments;

    private final Map<String, Object> conditions;

    @JsonCreator
    public TriggerEntry(@JsonProperty("id") UUID id,
                        @JsonProperty("projectId") UUID projectId,
                        @JsonProperty("projectName") String projectName,
                        @JsonProperty("repositoryId") UUID repositoryId,
                        @JsonProperty("repositoryName") String repositoryName,
                        @JsonProperty("eventSource") String eventSource,
                        @JsonProperty("entryPoint") String entryPoint,
                        @JsonProperty("arguments") Map<String, Object> arguments,
                        @JsonProperty("conditions") Map<String, Object> conditions) {

        this.id = id;
        this.repositoryId = repositoryId;
        this.repositoryName = repositoryName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.eventSource = eventSource;
        this.entryPoint = entryPoint;
        this.arguments = arguments;
        this.conditions = conditions;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getEventSource() {
        return eventSource;
    }

    public String getEntryPoint() {
        return entryPoint;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    @Override
    public String toString() {
        return "TriggerEntry{" +
                "id=" + id +
                ", repositoryId=" + repositoryId +
                ", repositoryName='" + repositoryName + '\'' +
                ", projectId=" + projectId +
                ", projectName='" + projectName + '\'' +
                ", eventSource='" + eventSource + '\'' +
                ", entryPoint='" + entryPoint + '\'' +
                ", arguments=" + arguments +
                ", conditions=" + conditions +
                '}';
    }
}
