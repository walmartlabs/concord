package com.walmartlabs.concord.server.org.project;

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

import com.fasterxml.jackson.annotation.*;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RepositoryEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    private final UUID projectId;

    @ConcordKey
    private final String name;

    @NotNull
    @Size(max = 2048)
    private final String url;

    @Size(max = 255)
    private final String branch;

    @Size(max = 64)
    private final String commitId;

    @Size(max = 2048)
    private final String path;

    private final UUID secretId;

    @ConcordKey
    @JsonAlias("secret")
    private final String secretName;

    private final String secretStoreType;

    private final boolean disabled;

    private final Map<String, Object> meta;

    private final boolean triggersDisabled;

    public RepositoryEntry(String name, String url) {
        this(null, null, name, url, null, null, null, false, null, null, null, null, false);
    }

    public RepositoryEntry(String name, RepositoryEntry e) {
        this(e.id, e.projectId, name, e.url, e.branch, e.commitId, e.path, e.disabled, e.getSecretId(), e.secretName, e.secretStoreType, e.meta, e.triggersDisabled);
    }

    public RepositoryEntry(RepositoryEntry e, String branch, String commitId) {
        this(e.id,
                e.projectId,
                e.name,
                e.url,
                branch,
                commitId,
                e.path,
                e.disabled,
                e.getSecretId(),
                e.secretName,
                e.secretStoreType,
                e.meta,
                e.triggersDisabled);
    }

    @JsonCreator
    public RepositoryEntry(@JsonProperty("id") UUID id,
                           @JsonProperty("projectId") UUID projectId,
                           @JsonProperty("name") String name,
                           @JsonProperty("url") String url,
                           @JsonProperty("branch") String branch,
                           @JsonProperty("commitId") String commitId,
                           @JsonProperty("path") String path,
                           @JsonProperty("disabled") boolean disabled,
                           @JsonProperty("secretId") UUID secretId,
                           @JsonProperty("secretName") String secretName,
                           @JsonProperty("secretStoreType") String secretStoreType,
                           @JsonProperty("meta") Map<String, Object> meta,
                           @JsonProperty("triggersDisabled") boolean triggersDisabled) {

        this.id = id;
        this.projectId = projectId;
        this.name = name;
        this.url = url;
        this.branch = branch;
        this.commitId = commitId;
        this.path = path;
        this.secretId = secretId;
        this.secretName = secretName;
        this.secretStoreType = secretStoreType;
        this.disabled = disabled;
        this.meta = meta;
        this.triggersDisabled = triggersDisabled;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getBranch() {
        return branch;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getPath() {
        return path;
    }

    public UUID getSecretId() {
        return secretId;
    }

    public String getSecretName() {
        return secretName;
    }

    public String getSecretStoreType() {
        return secretStoreType;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public boolean isTriggersDisabled() {
        return triggersDisabled;
    }

    public RepositoryEntry withBranch(String branch) {
        return new RepositoryEntry(id, projectId, name, url, branch, commitId, path, disabled, secretId, secretName, secretStoreType, meta, triggersDisabled);
    }

    public RepositoryEntry withPath(String path) {
        return new RepositoryEntry(id, projectId, name, url, branch, commitId, path, disabled, secretId, secretName, secretStoreType, meta, triggersDisabled);
    }

    public RepositoryEntry withDisabled(boolean disabled) {
        return new RepositoryEntry(id, projectId, name, url, branch, commitId, path, disabled, secretId, secretName, secretStoreType, meta, triggersDisabled);
    }

    @Override
    public String toString() {
        return "RepositoryEntry{" +
                "id=" + id +
                ", projectId=" + projectId +
                ", name='" + name + '\'' +
                ", url='" + url + '\'' +
                ", branch='" + branch + '\'' +
                ", commitId='" + commitId + '\'' +
                ", path='" + path + '\'' +
                ", secretId=" + secretId +
                ", secretName='" + secretName + '\'' +
                ", secretStoreType='" + secretStoreType + '\'' +
                ", disabled=" + disabled + '\'' +
                ", meta=" + meta + '\'' +
                ", triggersDisabled=" + triggersDisabled +
                '}';
    }
}
