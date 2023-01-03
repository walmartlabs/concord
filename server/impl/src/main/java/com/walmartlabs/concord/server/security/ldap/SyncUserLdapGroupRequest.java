package com.walmartlabs.concord.server.security.ldap;

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
import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

public class SyncUserLdapGroupRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private final String username;

    private final String userDomain;

    @JsonCreator
    public SyncUserLdapGroupRequest(@JsonProperty("username") String username, @JsonProperty("userDomain") String userDomain) {
        this.username = username;
        this.userDomain = userDomain;
    }

    public String getUsername() {
        return username;
    }

    public String getUserDomain() {
        return userDomain;
    }

    @Override
    public String toString() {
        return "SyncUserLdapGroupRequest{" +
                "username='" + username + '\'' +
                ", userDomain='" + userDomain + '\'' +
                '}';
    }
}
