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
import com.walmartlabs.concord.server.user.RoleDao;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;

@Named
public class ConcordShiroAuthorizer {

    private final UserManager userManager;
    private final RoleDao roleDao;

    @Inject
    public ConcordShiroAuthorizer(UserManager userManager, RoleDao roleDao) {
        this.userManager = userManager;
        this.roleDao = roleDao;
    }

    public AuthorizationInfo getAuthorizationInfo(UserPrincipal p, Collection<String> roles) {
        SimpleAuthorizationInfo i = new SimpleAuthorizationInfo();

        UserEntry u = userManager.get(p.getId())
                .orElse(null);

        if (u != null && u.getPermissions() != null) {
            i.addStringPermissions(u.getPermissions());
        }

        if (roles != null) {
            Collection<String> permissions = roleDao.getPermissions(roles);
            i.addStringPermissions(permissions);
        }

        return i;
    }
}
