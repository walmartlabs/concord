package com.walmartlabs.concord.server.api.process;

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ProcessEntry implements Serializable {

    private final UUID instanceId;
    private final ProcessKind kind;
    private final UUID parentInstanceId;
    private final UUID orgId;
    private final String orgName;
    private final UUID projectId;
    private final String projectName;
    private final String initiator;
    private final ProcessStatus status;
    private final String lastAgentId;
    private final String logFileName;
    private final Set<String> tags;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final Date createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final Date lastUpdatedAt;

    @JsonCreator
    public ProcessEntry(@JsonProperty("instanceId") UUID instanceId,
                        @JsonProperty("kind") ProcessKind kind,
                        @JsonProperty("parentInstanceId") UUID parentInstanceId,
                        @JsonProperty("orgId") UUID orgId,
                        @JsonProperty("orgName") String orgName,
                        @JsonProperty("projectId") UUID projectId,
                        @JsonProperty("projectName") String projectName,
                        @JsonProperty("createdAt") Date createdAt,
                        @JsonProperty("initiator") String initiator,
                        @JsonProperty("lastUpdatedAt") Date lastUpdatedAt,
                        @JsonProperty("status") ProcessStatus status,
                        @JsonProperty("lastAgentId") String lastAgentId,
                        @JsonProperty("tags") Set<String> tags) {

        this.instanceId = instanceId;
        this.kind = kind;
        this.parentInstanceId = parentInstanceId;
        this.orgId = orgId;
        this.orgName = orgName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.createdAt = createdAt;
        this.initiator = initiator;
        this.lastUpdatedAt = lastUpdatedAt;
        this.status = status;
        this.lastAgentId = lastAgentId;

        // TODO left for backwards compatibility
        this.logFileName = instanceId + ".log";

        this.tags = tags;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public ProcessKind getKind() {
        return kind;
    }

    public UUID getParentInstanceId() {
        return parentInstanceId;
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

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getInitiator() {
        return initiator;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public String getLastAgentId() {
        return lastAgentId;
    }

    public Date getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public String getLogFileName() {
        return logFileName;
    }

    public Set<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "ProcessEntry{" +
                "instanceId=" + instanceId +
                ", kind=" + kind +
                ", parentInstanceId=" + parentInstanceId +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", projectId=" + projectId +
                ", projectName='" + projectName + '\'' +
                ", createdAt=" + createdAt +
                ", initiator='" + initiator + '\'' +
                ", status=" + status +
                ", lastAgentId='" + lastAgentId + '\'' +
                ", lastUpdatedAt=" + lastUpdatedAt +
                ", logFileName='" + logFileName + '\'' +
                ", tags=" + tags +
                '}';
    }
}
