package com.walmartlabs.concord.server.api.org.secret;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class SecretEntry implements Serializable {

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
    private final SecretStoreType storeType;

    private final SecretEncryptedByType encryptedByType;

    private final SecretVisibility visibility;

    private final SecretOwner owner;

    @JsonCreator
    public SecretEntry(@JsonProperty("id") UUID id,
                       @JsonProperty("name") String name,
                       @JsonProperty("orgId") UUID orgId,
                       @JsonProperty("orgName") String orgName,
                       @JsonProperty("type") SecretType type,
                       @JsonProperty("encryptedBy") SecretEncryptedByType encryptedByType,
                       @JsonProperty("storeType") SecretStoreType storeType,
                       @JsonProperty("visibility") SecretVisibility visibility,
                       @JsonProperty("owner") SecretOwner owner) {

        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.orgName = orgName;
        this.type = type;
        this.encryptedByType = encryptedByType;
        this.storeType = storeType;
        this.visibility = visibility;
        this.owner = owner;
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


    public SecretEncryptedByType getEncryptedByType() {
        return encryptedByType;
    }

    public SecretStoreType getStoreType() {
        return storeType;
    }

    public SecretVisibility getVisibility() {
        return visibility;
    }

    public SecretOwner getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return "SecretEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", type=" + type +
                ", encryptedByType=" + encryptedByType +
                ", storeType=" + storeType +
                ", visibility=" + visibility +
                ", owner=" + owner +
                '}';
    }
}
