package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.project.ProjectEntry;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public class SecretEntryV2 extends SecretEntry {

    private final Set<ProjectEntry> projects;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final OffsetDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final OffsetDateTime lastUpdatedAt;

    @JsonCreator
    public SecretEntryV2(@JsonProperty("id") UUID id,
                         @JsonProperty("name") String name,
                         @JsonProperty("orgId") UUID orgId,
                         @JsonProperty("orgName") String orgName,
                         @JsonProperty("projects") Set<ProjectEntry> projects,
                         @JsonProperty("type") SecretType type,
                         @JsonProperty("encryptedBy") SecretEncryptedByType encryptedBy,
                         @JsonProperty("storeType") String storeType,
                         @JsonProperty("visibility") SecretVisibility visibility,
                         @JsonProperty("owner") EntityOwner owner,
                         @JsonProperty("createdAt") OffsetDateTime createdAt,
                         @JsonProperty("lastUpdatedAt") OffsetDateTime lastUpdatedAt,
                         byte[] secretSalt,
                         HashAlgorithm hashAlgorithm
    ) {
        super(id, name, orgId, orgName, projects.stream().map(ProjectEntry::getId).findFirst().orElse(null),
                projects.stream().map(ProjectEntry::getName).findFirst().orElse(null), type,
                encryptedBy, storeType, visibility, owner, secretSalt, hashAlgorithm);
        this.projects = projects;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public Set<ProjectEntry> getProjects() {
        return projects;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }
}
