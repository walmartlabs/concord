package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.cfg.LdapConfiguration;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Set;
import java.util.UUID;

public class LdapUserInfoProvider implements UserInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapUserInfoProvider.class);

    private final UserDao userDao;
    private final LdapManager ldapManager;
    private final LdapConfiguration cfg;

    @Inject
    public LdapUserInfoProvider(UserDao userDao,
                                LdapManager ldapManager,
                                LdapConfiguration cfg) {

        this.userDao = userDao;
        this.ldapManager = ldapManager;
        this.cfg = cfg;
    }

    @Override
    public UserType getUserType() {
        return UserType.LDAP;
    }

    @Override
    public UserInfo getInfo(UUID id, String username, String userDomain) {
        try {
            LdapPrincipal p = ldapManager.getPrincipal(username, userDomain);
            return buildInfo(id, p);
        } catch (Exception e) {
            log.error("getInfo ['{}'] -> error", username, e);
            throw new ConcordApplicationException("Error while retrieving LDAP information for " + username, e);
        }
    }

    @Override
    public UUID create(String username, String userDomain, String displayName, String email, Set<String> roles) {
        if (!Roles.isAdmin() && !cfg.isAutoCreateUsers()) {
            // unfortunately there's no easy way to throw a custom authentication error and keep the original message
            // this will result in a 401 response with an empty body anyway
            throw new ConcordApplicationException("Automatic creation of users is disabled.");
        }

        UserInfo info = getInfo(null, username, userDomain);
        if (info == null) {
            throw new ConcordApplicationException("User '" + username + "' with domain '" + userDomain + "' not found in LDAP");
        }

        return userDao.insertOrUpdate(info.username(), info.userDomain(), info.displayName(), info.email(), UserType.LDAP, roles);
    }
    
    private static UserInfo buildInfo(UUID id, LdapPrincipal p) {
        if (p == null) {
            return null;
        }

        return UserInfo.builder()
                .id(id)
                .username(p.getUsername())
                .userDomain(p.getDomain())
                .displayName(p.getDisplayName())
                .email(p.getEmail())
                .groups(p.getGroups())
                .attributes(p.getAttributes())
                .build();
    }
}
