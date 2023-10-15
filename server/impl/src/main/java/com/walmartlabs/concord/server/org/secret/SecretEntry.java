package com.walmartlabs.concord.server.org.secret;

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
import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.org.EntityOwner;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
@Deprecated
public class SecretEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    private final UUID projectId;

    @ConcordKey
    private final String projectName;

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

    @JsonCreator
    public SecretEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("orgId") UUID orgId,
                       @JsonProperty("orgName") String orgName,
                       @JsonProperty("projectId") UUID projectId,
                       @JsonProperty("projectName") String projectName,
                       @JsonProperty("type") SecretType type,
                       @JsonProperty("encryptedBy") SecretEncryptedByType encryptedBy,
                       @JsonProperty("storeType") String storeType,
                       @JsonProperty("visibility") SecretVisibility visibility,
                       @JsonProperty("owner") EntityOwner owner,
                       byte[] secretSalt,
                       HashAlgorithm hashAlgorithm
    ) {

        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.orgName = orgName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.type = type;
        this.encryptedBy = encryptedBy;
        this.storeType = storeType;
        this.visibility = visibility;
        this.owner = owner;
        this.secretSalt = secretSalt;
        this.hashAlgorithm = hashAlgorithm;
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

    public UUID getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
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

    @Override
    public String toString() {
        return "SecretEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", projectId='" + projectId + '\'' +
                ", projectName='" + projectName + '\'' +
                ", type=" + type +
                ", encryptedBy=" + encryptedBy +
                ", storeType=" + storeType +
                ", visibility=" + visibility +
                ", owner=" + owner +
                '}';
    }
}
