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
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.sdk.Constants;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TriggerEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    @ConcordKey
    private final UUID orgId;

    private final String orgName;

    private final UUID projectId;

    @ConcordKey
    private final String projectName;

    private final UUID repositoryId;

    @ConcordKey
    private final String repositoryName;

    @NotNull
    @ConcordKey
    private final String eventSource;

    private final List<String> activeProfiles;

    private final Map<String, Object> arguments;

    private final Map<String, Object> conditions;

    @NotNull
    private final Map<String, Object> cfg;

    @JsonCreator
    public TriggerEntry(@JsonProperty("id") UUID id,
                        @JsonProperty("orgId") UUID orgId,
                        @JsonProperty("orgName") String orgName,
                        @JsonProperty("projectId") UUID projectId,
                        @JsonProperty("projectName") String projectName,
                        @JsonProperty("repositoryId") UUID repositoryId,
                        @JsonProperty("repositoryName") String repositoryName,
                        @JsonProperty("eventSource") String eventSource,
                        @JsonProperty("activeProfiles") List<String> activeProfiles,
                        @JsonProperty("arguments") Map<String, Object> arguments,
                        @JsonProperty("conditions") Map<String, Object> conditions,
                        @JsonProperty("cfg") Map<String, Object> cfg) {

        this.id = id;
        this.orgId = orgId;
        this.orgName = orgName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.repositoryId = repositoryId;
        this.repositoryName = repositoryName;
        this.eventSource = eventSource;
        this.activeProfiles = activeProfiles;
        this.arguments = arguments;
        this.conditions = conditions;
        this.cfg = cfg;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public String getEventSource() {
        return eventSource;
    }

    @JsonIgnore
    public String getEntryPoint() {
        if (cfg == null) {
            return null;
        }

        return (String) cfg.get(Constants.Request.ENTRY_POINT_KEY);
    }

    @JsonIgnore
    public boolean isUseInitiator() {
        return getBoolean(cfg, Constants.Trigger.USE_INITIATOR);
    }

    @JsonIgnore
    public boolean isUseEventCommitId() {
        return getBoolean(cfg, Constants.Trigger.USE_EVENT_COMMIT_ID);
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

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public String toString() {
        return "TriggerEntry{" +
                "id=" + id +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", projectId=" + projectId +
                ", projectName='" + projectName + '\'' +
                ", repositoryId=" + repositoryId +
                ", repositoryName='" + repositoryName + '\'' +
                ", eventSource='" + eventSource + '\'' +
                ", activeProfiles=" + activeProfiles +
                ", arguments=" + arguments +
                ", conditions=" + conditions +
                ", cfg=" + cfg +
                '}';
    }

    private static boolean getBoolean(Map<String, Object> cfg, String k) {
        if (cfg == null) {
            return false;
        }

        Boolean v = (Boolean) cfg.get(k);
        if (v == null) {
            return false;
        }

        return v;
    }
}
