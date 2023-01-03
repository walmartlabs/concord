package com.walmartlabs.concord.server.org.team;

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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.UUID;

@JsonInclude(Include.NON_NULL)
public class TeamUserEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID userId;

    @Size(max = UserEntry.MAX_USERNAME_LENGTH)
    private final String username;

    @Size(max = UserEntry.MAX_DOMAIN_LENGTH)
    private final String userDomain;

    private final String displayName;

    private final UserType userType;

    private final TeamRole role;

    private final TeamMemberType memberType;

    private final String ldapGroupSource;

    @JsonCreator
    public TeamUserEntry(@JsonProperty("userId") UUID userId,
                         @JsonProperty("username") String username,
                         @JsonProperty("userDomain") String userDomain,
                         @JsonProperty("displayName") String displayName,
                         @JsonProperty("userType") UserType userType,
                         @JsonProperty("role") TeamRole role,
                         @JsonProperty("memberType") TeamMemberType memberType,
                         @JsonProperty("ldapGroupSource") String ldapGroupSource) {

        this.userId = userId;
        this.username = username;
        this.userDomain = userDomain;
        this.displayName = displayName;
        this.userType = userType;
        this.role = role;
        this.memberType = memberType;
        this.ldapGroupSource = ldapGroupSource;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getUserDomain() {
        return userDomain;
    }

    public String getDisplayName() {
        return displayName;
    }

    public UserType getUserType() {
        return userType;
    }

    public TeamRole getRole() {
        return role;
    }

    public TeamMemberType getMemberType() {
        return memberType;
    }

    public String getLdapGroupSource() {
        return ldapGroupSource;
    }

    @Override
    public String toString() {
        return "TeamUserEntry{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", userDomain='" + userDomain + '\'' +
                ", displayName='" + displayName + '\'' +
                ", userType=" + userType +
                ", role=" + role +
                ", memberType=" + memberType +
                ", ldapGroupSource='" + ldapGroupSource + '\'' +
                '}';
    }
}
