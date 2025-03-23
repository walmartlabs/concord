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

import com.fasterxml.jackson.annotation.*;
import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.project.ProjectEntry;

import javax.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SecretEntryV2 implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    @NotNull
    private final SecretType type;

    @NotNull
    private final String storeType;

    private final SecretEncryptedByType encryptedBy;

    private final SecretVisibility visibility;

    private final EntityOwner owner;

    @JsonIgnore
    private final byte[] secretSalt;

    @JsonIgnore
    private final HashAlgorithm hashAlgorithm;

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
        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.orgName = orgName;
        this.type = type;
        this.encryptedBy = encryptedBy;
        this.storeType = storeType;
        this.visibility = visibility;
        this.owner = owner;
        this.secretSalt = secretSalt;
        this.hashAlgorithm = hashAlgorithm;
        this.projects = projects;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getOrgId() {
        return orgId;
    }

    public String getOrgName() {
        return orgName;
    }

    public SecretType getType() {
        return type;
    }

    public SecretEncryptedByType getEncryptedBy() {
        return encryptedBy;
    }

    public String getStoreType() {
        return storeType;
    }

    public SecretVisibility getVisibility() {
        return visibility;
    }

    public EntityOwner getOwner() {
        return owner;
    }

    public byte[] getSecretSalt() {
        return secretSalt;
    }

    public HashAlgorithm getHashAlgorithm() {
        return hashAlgorithm;
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
