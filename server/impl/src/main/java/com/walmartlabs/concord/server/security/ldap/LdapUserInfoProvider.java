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
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;
import java.util.UUID;

@Named
@Singleton
public class LdapUserInfoProvider implements UserInfoProvider {

    private static final Logger log = LoggerFactory.getLogger(LdapUserInfoProvider.class);

    private final LdapManager ldapManager;

    @Inject
    public LdapUserInfoProvider(LdapManager ldapManager) {
        this.ldapManager = ldapManager;
    }

    @Override
    public UserType getUserType() {
        return UserType.LDAP;
    }

    @Override
    public UserInfo getInfo(UUID id, String username) {
        try {
            LdapPrincipal p = ldapManager.getPrincipal(username);
            return getInfo(id, username, p);
        } catch (NamingException e) {
            log.error("getInfo ['{}'] -> error", username, e);
            throw new ConcordApplicationException("Error while retrieving LDAP information for " + username, e);
        }
    }

    private static UserInfo getInfo(UUID id, String username, LdapPrincipal p) {
        if (p == null) {
            return null;
        }

        return UserInfo.builder()
                .id(id)
                .username(username)
                .displayName(p.getDisplayName())
                .email(p.getEmail())
                .groups(p.getGroups())
                .attributes(p.getAttributes())
                .build();
    }
}
