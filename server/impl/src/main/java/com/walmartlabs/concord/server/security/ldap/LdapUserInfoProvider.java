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

import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.UUID;

@Named
@Singleton
public class LdapUserInfoProvider implements UserInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapUserInfoProvider.class);
    
    private final LdapManager ldapManager;
    private final UserDao userDao;
    private static final String SSO_REALM_NAME = "sso";

    @Inject
    public LdapUserInfoProvider(LdapManager ldapManager, UserDao userDao) {
        this.ldapManager = ldapManager;
        this.userDao = userDao;
    }

    @Override
    public UserType getUserType() {
        return UserType.LDAP;
    }

    @Override
    public UserInfo getInfo(UUID id, String username, String userDomain) {
        /* Get User Data from database when user logged in via SSO */
        UserPrincipal u = UserPrincipal.getCurrent();
        if (u != null && u.getRealm().equals(SSO_REALM_NAME)){
            return getInfoDao(id, username, userDomain);
        }
        
        try {
            LdapPrincipal p = ldapManager.getPrincipal(username, userDomain);
            return buildInfo(id, p);
        } catch (Exception e) {
            log.error("getInfo ['{}'] -> error", username, e);
            throw new ConcordApplicationException("Error while retrieving LDAP information for " + username, e);
        }
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

    private UserInfo getInfoDao(UUID id, String username, String userDomain) {
        if (id == null) {
            id = userDao.getId(username, userDomain, UserType.LDAP);
        }

        if (id == null) {
            return null;
        }

        UserEntry e = userDao.get(id);
        if (e == null) {
            return null;
        }

        return UserInfo.builder()
                .id(id)
                .username(e.getName())
                .userDomain(userDomain)
                .displayName(e.getDisplayName())
                .email(e.getEmail())
                .build();
    }
}
