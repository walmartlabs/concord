package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;

import java.io.Serializable;
import java.util.UUID;

import static java.util.Objects.requireNonNull;

/**
 * <b>Note:</b> this class is serialized when user principals are stored in
 * the process state. It must maintain backward compatibility.
 */
public class UserPrincipal implements Serializable {

    private static final long serialVersionUID = 1L;

    public static UserPrincipal getCurrent() {
        return SecurityUtils.getCurrent(UserPrincipal.class);
    }

    public static UserPrincipal assertCurrent() {
        return SecurityUtils.assertCurrent(UserPrincipal.class);
    }

    private final String realm;
    private final UserEntry user;

    public UserPrincipal(String realm, UserEntry user) {
        this.realm = requireNonNull(realm);
        this.user = requireNonNull(user);
    }

    public String getRealm() {
        return realm;
    }

    public UserEntry getUser() {
        return user;
    }

    public UUID getId() {
        return user.getId();
    }

    public String getUsername() {
        return user.getName();
    }

    public String getDomain() {
        return user.getDomain();
    }

    public UserType getType() {
        return user.getType();
    }

    @Override
    public String toString() {
        return "UserPrincipal{" +
                "realm='" + realm + '\'' +
                ", user=" + user +
                '}';
    }
}
