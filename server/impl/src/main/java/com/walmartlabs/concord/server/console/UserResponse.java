package com.walmartlabs.concord.server.console;

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
import com.walmartlabs.concord.server.org.OrganizationEntry;

import java.io.Serializable;
import java.util.Set;

@JsonInclude(Include.NON_EMPTY)
public class UserResponse implements Serializable {

    private final String realm;
    private final String username;
    private final String userDomain;
    private final String displayName;
    private final Set<OrganizationEntry> orgs;

    @JsonCreator
    public UserResponse(@JsonProperty("realm") String realm,
                        @JsonProperty("username") String username,
                        @JsonProperty("userDomain") String userDomain,
                        @JsonProperty("displayName") String displayName,
                        @JsonProperty("orgs") Set<OrganizationEntry> orgs) {

        this.realm = realm;
        this.username = username;
        this.userDomain = userDomain;
        this.displayName = displayName;
        this.orgs = orgs;
    }

    public String getRealm() {
        return realm;
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

    public Set<OrganizationEntry> getOrgs() {
        return orgs;
    }

    @Override
    public final String toString() {
        return "UserResponse{" +
                "realm='" + realm + '\'' +
                ", username='" + username + '\'' +
                ", userDomain='" + userDomain + '\'' +
                ", displayName='" + displayName + '\'' +
                ", orgs=" + orgs +
                '}';
    }
}
