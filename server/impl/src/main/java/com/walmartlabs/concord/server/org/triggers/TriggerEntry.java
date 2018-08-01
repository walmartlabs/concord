package com.walmartlabs.concord.server.org.triggers;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
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

    private final List<String> activeProfiles;

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
                        @JsonProperty("activeProfiles") List<String> activeProfiles,
                        @JsonProperty("arguments") Map<String, Object> arguments,
                        @JsonProperty("conditions") Map<String, Object> conditions) {

        this.id = id;
        this.repositoryId = repositoryId;
        this.repositoryName = repositoryName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.eventSource = eventSource;
        this.entryPoint = entryPoint;
        this.activeProfiles = activeProfiles;
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

    public List<String> getActiveProfiles() {
        return activeProfiles;
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
                ", activeProfiles=" + activeProfiles +
                ", arguments=" + arguments +
                ", conditions=" + conditions +
                '}';
    }
}
