package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.api.user.UserEntry;
import com.walmartlabs.concord.server.api.user.UserType;

import java.io.Serializable;
import java.util.UUID;

public class UserPrincipal implements Serializable {

    public static UserPrincipal getCurrent() {
        return PrincipalUtils.getCurrent(UserPrincipal.class);
    }

    public static UserPrincipal assertCurrent() {
        return PrincipalUtils.assertCurrent(UserPrincipal.class);
    }

    private final String realm;
    private final UUID id;
    private final String username;
    private final boolean admin;
    private final UserType type;

    public UserPrincipal(String realm, UserEntry e) {
        this(realm, e.getId(), e.getName(), e.isAdmin(), e.getType());
    }

    public UserPrincipal(String realm, UUID id, String username, boolean admin, UserType type) {
        this.realm = realm;
        this.id = id;
        this.username = username;
        this.admin = admin;
        this.type = type;
    }

    public String getRealm() {
        return realm;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public boolean isAdmin() {
        return admin;
    }

    public UserType getType() {
        return type;
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "realm='" + realm + '\'' +
                ", id=" + id +
                ", username='" + username + '\'' +
                ", admin=" + admin +
                ", type=" + type +
                '}';
    }
}
