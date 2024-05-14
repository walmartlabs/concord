package com.walmartlabs.concord.server.user;

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
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.org.OrganizationEntry;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class UserEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int MAX_USERNAME_LENGTH = 128;
    public static final int MAX_DOMAIN_LENGTH = 512;
    public static final int MAX_DISPLAY_NAME_LENGTH = 1024;
    public static final int MAX_EMAIL_LENGTH = 512;

    private final UUID id;

    @Size(max = MAX_USERNAME_LENGTH)
    private final String name;

    @Size(max = MAX_DOMAIN_LENGTH)
    private final String domain;

    @Size(max = MAX_DISPLAY_NAME_LENGTH)
    private final String displayName;

    private final Set<OrganizationEntry> orgs;

    private final UserType type;

    @Size(max = MAX_EMAIL_LENGTH)
    private final String email;

    private final Set<RoleEntry> roles;

    private final boolean disabled;

    private final boolean permanentlyDisabled;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSX")
    private final OffsetDateTime disabledDate;

    @JsonCreator
    public UserEntry(@JsonProperty("id") UUID id,
                     @JsonProperty("name") String name,
                     @JsonProperty("domain") String domain,
                     @JsonProperty("displayName") String displayName,
                     @JsonProperty("orgs") Set<OrganizationEntry> orgs,
                     @JsonProperty("type") UserType type,
                     @JsonProperty("email") String email,
                     @JsonProperty("roles") Set<RoleEntry> roles,
                     @JsonProperty("disabled") boolean disabled,
                     @JsonProperty("disabledDate") OffsetDateTime disabledDate,
                     @JsonProperty("permanentlyDisabled") boolean permanentlyDisabled) {

        this.id = id;
        this.name = name;
        this.domain = domain;
        this.displayName = displayName;
        this.orgs = orgs;
        this.type = type;
        this.email = email;
        this.roles = roles;
        this.disabled = disabled;
        this.disabledDate = disabledDate;
        this.permanentlyDisabled = permanentlyDisabled;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDomain() {
        return domain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<OrganizationEntry> getOrgs() {
        return orgs;
    }

    public UserType getType() {
        return type;
    }

    public String getEmail() {
        return email;
    }

    public Set<RoleEntry> getRoles() {
        return roles;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public OffsetDateTime getDisabledDate() {
        return disabledDate;
    }

    public boolean isPermanentlyDisabled() {
        return permanentlyDisabled;
    }

    @Override
    public String toString() {
        return "UserEntry{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", domain='" + domain + '\'' +
                ", displayName='" + displayName + '\'' +
                ", orgs=" + orgs +
                ", type=" + type +
                ", email='" + email + '\'' +
                ", roles=" + roles +
                ", disabled=" + disabled +
                ", disabledDate=" + disabledDate +
                ", permanentlyDisabled=" + permanentlyDisabled +
                '}';
    }
}
