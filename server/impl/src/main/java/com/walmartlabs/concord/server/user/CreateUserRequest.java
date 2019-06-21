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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmartlabs.concord.common.validation.ConcordUsername;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Set;

public class CreateUserRequest implements Serializable {

    @NotNull
    @ConcordUsername
    @Size(max = UserEntry.MAX_USERNAME_LENGTH)
    private final String username;

    @Size(max = UserEntry.MAX_DOMAIN_LENGTH)
    private final String userDomain;

    @Size(max = UserEntry.MAX_DISPLAY_NAME_LENGTH)
    private final String displayName;

    @Size(max = UserEntry.MAX_EMAIL_LENGTH)
    private final String email;

    private final UserType type;

    private final boolean disabled;

    private final Set<String> roles;

    @JsonCreator
    public CreateUserRequest(@JsonProperty("username") String username,
                             @JsonProperty("userDomain") String userDomain,
                             @JsonProperty("displayName") String displayName,
                             @JsonProperty("email") String email,
                             @JsonProperty("userType") UserType type,
                             @JsonProperty("disabled") boolean disabled,
                             @JsonProperty("roles") Set<String> roles) {

        this.username = username;
        this.userDomain = userDomain;
        this.displayName = displayName;
        this.email = email;
        this.type = type;
        this.disabled = disabled;
        this.roles = roles;
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

    public String getEmail() {
        return email;
    }

    public UserType getType() {
        return type;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public Set<String> getRoles() {
        return roles;
    }

    @Override
    public String toString() {
        return "CreateUserRequest{" +
                "username='" + username + '\'' +
                ", userDomain='" + userDomain + '\'' +
                ", displayName='" + displayName + '\'' +
                ", email='" + email + '\'' +
                ", type=" + type +
                ", disabled=" + disabled +
                ", roles=" + roles +
                '}';
    }
}
