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

import com.walmartlabs.concord.server.user.UserInfoProducer;
import com.walmartlabs.concord.server.user.UserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;

@Named
@Singleton
public class LdapUserInfoProducer implements UserInfoProducer {

    private static final Logger log = LoggerFactory.getLogger(LdapUserInfoProducer.class);

    private final LdapManager ldapManager;

    @Inject
    public LdapUserInfoProducer(LdapManager ldapManager) {
        this.ldapManager = ldapManager;
    }

    @Override
    public UserType getUserType() {
        return UserType.LDAP;
    }

    @Override
    public UserInfo getInfo(String username) {
        try {
            LdapPrincipal p = ldapManager.getPrincipal(username);
            return UserInfo.builder()
                    .displayName(p.getDisplayName())
                    .email(p.getEmail())
                    .putAttributes("groups", p.getGroups())
                    .putAttributes("attributes", p.getAttributes())
                    .build();
        } catch (NamingException e) {
            log.error("getInfo ['{}'] -> error", username, e);
            throw new RuntimeException("Error while retrieving LDAP information for " + username, e);
        }
    }
}
