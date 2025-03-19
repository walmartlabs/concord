package com.walmartlabs.concord.server.org.project;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.jooq.enums.OutVariablesMode;
import com.walmartlabs.concord.server.jooq.enums.ProcessExecMode;
import com.walmartlabs.concord.server.jooq.enums.RawPayloadMode;
import com.walmartlabs.concord.server.org.EntityOwner;

import javax.validation.Valid;
import javax.validation.constraints.Size;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class ProjectEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static ProjectEntry replace(ProjectEntry e, Map<String, RepositoryEntry> repos) {
        return new ProjectEntry(e.id, e.name, e.description, e.orgId, e.orgName, repos,
                e.cfg, e.visibility, e.owner, e.rawPayloadMode, e.meta, e.outVariablesMode,
                e.processExecMode, e.createdAt);
    }

    private final UUID id;

    @ConcordKey
    // TODO it should be final, but swagger makes it readOnly and provides no way to set the value
    private String name;

    @Size(max = 1024)
    private final String description;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    @Deprecated
    @Valid
    private final Map<String, RepositoryEntry> repositories;

    private final Map<String, Object> cfg;

    private final ProjectVisibility visibility;

    private final EntityOwner owner;

    private final RawPayloadMode rawPayloadMode;

    private final OutVariablesMode outVariablesMode;

    private final ProcessExecMode processExecMode;

    private final Map<String, Object> meta;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final OffsetDateTime createdAt;

    public ProjectEntry(String name) {
        this(null, name, null, null, null, null, null, null, null, RawPayloadMode.DISABLED, null, OutVariablesMode.DISABLED, ProcessExecMode.READERS, null);
    }

    public ProjectEntry(String name, ProjectVisibility visibility) {
        this(null, name, null, null, null, null, null, visibility, null, RawPayloadMode.DISABLED, null, OutVariablesMode.DISABLED, ProcessExecMode.READERS, null);
    }

    public ProjectEntry(String name, Map<String, RepositoryEntry> repositories) {
        this(null, name, null, null, null, repositories, null, null, null, RawPayloadMode.DISABLED, null, OutVariablesMode.DISABLED, ProcessExecMode.READERS, null);
    }

    public ProjectEntry(String name, UUID id) {
        this(id, name, null, null, null, null, null, null, null, RawPayloadMode.DISABLED, null, OutVariablesMode.DISABLED, ProcessExecMode.READERS, null);
    }

    @JsonCreator
    public ProjectEntry(@JsonProperty("id") UUID id,
                        @JsonProperty("name") String name,
                        @JsonProperty("description") String description,
                        @JsonProperty("orgId") UUID orgId,
                        @JsonProperty("orgName") String orgName,
                        @JsonProperty("repositories") Map<String, RepositoryEntry> repositories,
                        @JsonProperty("cfg") Map<String, Object> cfg,
                        @JsonProperty("visibility") ProjectVisibility visibility,
                        @JsonProperty("owner") EntityOwner owner,
                        @JsonProperty("rawPayloadMode") RawPayloadMode rawPayloadMode,
                        @JsonProperty("meta") Map<String, Object> meta,
                        @JsonProperty("outVariablesMode") OutVariablesMode outVariablesMode,
                        @JsonProperty("processExecMode") ProcessExecMode processExecMode,
                        @JsonProperty("createdAt") OffsetDateTime createdAt) {

        this.id = id;
        this.name = name;
        this.description = description;
        this.orgId = orgId;
        this.orgName = orgName;
        this.repositories = repositories;
        this.cfg = cfg;
        this.visibility = visibility;
        this.owner = owner;
        this.rawPayloadMode = rawPayloadMode;
        this.meta = meta;
        this.outVariablesMode = outVariablesMode;
        this.processExecMode = processExecMode;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public Map<String, RepositoryEntry> getRepositories() {
        return repositories;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    public ProjectVisibility getVisibility() {
        return visibility;
    }

    public EntityOwner getOwner() {
        return owner;
    }

    public RawPayloadMode getRawPayloadMode() {
        return rawPayloadMode;
    }

    public OutVariablesMode getOutVariablesMode() {
        return outVariablesMode;
    }

    public ProcessExecMode getProcessExecMode() {
        return processExecMode;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "ProjectEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", repositories=" + repositories +
                ", cfg=" + cfg +
                ", visibility=" + visibility +
                ", owner=" + owner +
                ", rawPayloadMode=" + rawPayloadMode +
                ", outVariablesMode=" + outVariablesMode +
                ", meta=" + meta +
                ", createdAt=" + createdAt +
                '}';
    }
}
