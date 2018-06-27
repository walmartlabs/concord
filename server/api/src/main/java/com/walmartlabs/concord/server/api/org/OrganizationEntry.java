package com.walmartlabs.concord.server.api.org;

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

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class OrganizationEntry implements Serializable {

    private final UUID id;

    @ConcordKey
    private final String name;

    private final OrganizationVisibility visibility;

    private final Map<String, Object> meta;

    public OrganizationEntry(String name) {
        this(null, name, null, null);
    }

    public OrganizationEntry(String name, OrganizationVisibility visibility) {
        this(null, name, visibility, null);
    }

    public OrganizationEntry(String name, Map<String, Object> meta) {
        this(null, name, null, meta);
    }

    @JsonCreator
    public OrganizationEntry(@JsonProperty("id") UUID id,
                             @JsonProperty("name") String name,
                             @JsonProperty("visibility") OrganizationVisibility visibility,
                             @JsonProperty("meta") Map<String, Object> meta) {
        this.id = id;
        this.name = name;
        this.visibility = visibility;
        this.meta = meta;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public OrganizationVisibility getVisibility() {
        return visibility;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    @Override
    public String toString() {
        return "OrganizationEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", visibility=" + visibility +
                ", meta=" + meta +
                '}';
    }
}
