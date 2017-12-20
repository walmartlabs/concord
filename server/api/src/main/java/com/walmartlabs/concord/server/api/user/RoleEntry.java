package com.walmartlabs.concord.server.api.user;

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

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class RoleEntry implements Serializable {

    @NotNull
    @ConcordKey
    private final String name;
    private final String description;
    private final Set<String> permissions;

    @JsonCreator
    public RoleEntry(@JsonProperty("name") String name,
                     @JsonProperty("description") String description,
                     @JsonProperty("permissions") Set<String> permissions) {

        this.name = name;
        this.description = description;
        this.permissions = permissions;
    }

    public RoleEntry(String name, String description, String... permissions) {
        this(name, description, new HashSet<>(Arrays.asList(permissions)));
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "RoleEntry{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
