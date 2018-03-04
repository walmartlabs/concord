package com.walmartlabs.concord.server.api.org.inventory;

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
import com.walmartlabs.concord.common.validation.ConcordKey;
import com.walmartlabs.concord.server.api.org.project.ProjectOwner;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class InventoryEntry implements Serializable {

    private final UUID id;

    @NotNull
    @ConcordKey
    private final String name;

    private final UUID orgId;

    @ConcordKey
    private final String orgName;

    private final InventoryVisibility visibility;

    private final InventoryOwner owner;

    private final InventoryEntry parent;

    public InventoryEntry(String name) {
        this(null, name, null, null, null, null, null);
    }

    @JsonCreator
    public InventoryEntry(@JsonProperty("id") UUID id,
                          @JsonProperty("name") String name,
                          @JsonProperty("orgId") UUID orgId,
                          @JsonProperty("orgName") String orgName,
                          @JsonProperty("visibility") InventoryVisibility visibility,
                          @JsonProperty("owner") InventoryOwner owner,
                          @JsonProperty("parent") InventoryEntry parent) {

        this.id = id;
        this.name = name;
        this.orgId = orgId;
        this.orgName = orgName;
        this.visibility = visibility;
        this.owner = owner;
        this.parent = parent;
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

    public UUID getId() {
        return id;
    }

    public InventoryVisibility getVisibility() {
        return visibility;
    }

    public InventoryEntry getParent() {
        return parent;
    }

    public InventoryOwner getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        return "InventoryEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", orgId=" + orgId +
                ", orgName='" + orgName + '\'' +
                ", visibility=" + visibility +
                ", owner=" + owner +
                ", parent=" + parent +
                '}';
    }
}
