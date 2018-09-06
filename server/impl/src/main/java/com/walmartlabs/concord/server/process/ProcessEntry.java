package com.walmartlabs.concord.server.process;

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
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;
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
    private final UUID repoId;
    private final String repoName;
    private final String repoUrl;
    private final String repoPath;
    private final String commitId;
    private final String commitMsg;
    private final String initiator;
    private final UUID initiatorId;
    private final ProcessStatus status;
    private final String lastAgentId;
    private final String logFileName;
    private final Set<String> tags;
    private final Set<UUID> childrenIds;
    private final Map<String, Object> meta;

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
                        @JsonProperty("repoId") UUID repoId,
                        @JsonProperty("repoName") String repoName,
                        @JsonProperty("repoUrl") String repoUrl,
                        @JsonProperty("repoPath") String repoPath,
                        @JsonProperty("commitId") String commitId,
                        @JsonProperty("commitMsg") String commitMsg,
                        @JsonProperty("createdAt") Date createdAt,
                        @JsonProperty("initiator") String initiator,
                        @JsonProperty("initiatorId") UUID initiatorId,
                        @JsonProperty("lastUpdatedAt") Date lastUpdatedAt,
                        @JsonProperty("status") ProcessStatus status,
                        @JsonProperty("lastAgentId") String lastAgentId,
                        @JsonProperty("tags") Set<String> tags,
                        @JsonProperty("childrenIds") Set<UUID> childrenIds,
                        @JsonProperty("meta") Map<String, Object> meta) {

        this.instanceId = instanceId;
        this.kind = kind;
        this.parentInstanceId = parentInstanceId;
        this.orgId = orgId;
        this.orgName = orgName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.repoId = repoId;
        this.repoName = repoName;
        this.repoUrl = repoUrl;
        this.repoPath = repoPath;
        this.commitId = commitId;
        this.commitMsg = commitMsg;
        this.createdAt = createdAt;
        this.initiator = initiator;
        this.initiatorId = initiatorId;
        this.lastUpdatedAt = lastUpdatedAt;
        this.status = status;
        this.lastAgentId = lastAgentId;

        // TODO left for backwards compatibility
        this.logFileName = instanceId + ".log";

        this.tags = tags;
        this.childrenIds = childrenIds;
        this.meta = meta;
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

    public UUID getRepoId() {
        return repoId;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getRepoPath() {
        return repoPath;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getCommitMsg() {
        return commitMsg;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public String getInitiator() {
        return initiator;
    }

    public UUID getInitiatorId() {
        return initiatorId;
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

    public Set<UUID> getChildrenIds() {
        return childrenIds;
    }

    public Map<String, Object> getMeta() {
        return meta;
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
                ", repoId=" + repoId +
                ", repoName='" + repoName + '\'' +
                ", repoUrl='" + repoUrl + '\'' +
                ", repoPath='" + repoPath + '\'' +
                ", commitId='" + commitId + '\'' +
                ", commitMsg='" + commitMsg + '\'' +
                ", initiator='" + initiator + '\'' +
                ", initiatorId='" + initiatorId + '\'' +
                ", status=" + status +
                ", lastAgentId='" + lastAgentId + '\'' +
                ", logFileName='" + logFileName + '\'' +
                ", tags=" + tags +
                ", childrenIds=" + childrenIds +
                ", createdAt=" + createdAt +
                ", lastUpdatedAt=" + lastUpdatedAt +
                ", meta=" + meta +
                '}';
    }
}
