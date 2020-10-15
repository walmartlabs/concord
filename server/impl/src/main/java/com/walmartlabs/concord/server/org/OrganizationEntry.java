package com.walmartlabs.concord.server.org;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordKey;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class OrganizationEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID id;

    @ConcordKey
    private final String name;

    @Nullable
    private final EntityOwner owner;

    private final OrganizationVisibility visibility;

    private final Map<String, Object> meta;

    private final Map<String, Object> cfg;

    public OrganizationEntry(String name) {
        this(null, name, null, null, null, null);
    }

    public OrganizationEntry(String name, OrganizationVisibility visibility) {
        this(null, name, null, visibility, null, null);
    }

    public OrganizationEntry(String name, Map<String, Object> meta) {
        this(null, name, null, null, meta, null);
    }

    @JsonCreator
    public OrganizationEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("name") String name,
                             @JsonProperty("owner") EntityOwner owner,
                             @JsonProperty("visibility") OrganizationVisibility visibility,
                             @JsonProperty("meta") Map<String, Object> meta,
                             @JsonProperty("cfg") Map<String, Object> cfg) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.visibility = visibility;
        this.meta = meta;
        this.cfg = cfg;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Nullable
    public EntityOwner getOwner() {
        return owner;
    }

    public OrganizationVisibility getVisibility() {
        return visibility;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public Map<String, Object> getCfg() {
        return cfg;
    }

    @Override
    public final String toString() {
        return "OrganizationEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", owner=" + owner +
                ", visibility=" + visibility +
                ", meta=" + meta +
                ", cfg=" + cfg +
                '}';
    }
}
