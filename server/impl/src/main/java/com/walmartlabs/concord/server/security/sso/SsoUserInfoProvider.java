package com.walmartlabs.concord.server.security.sso;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2021 Walmart Inc.
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

import com.walmartlabs.concord.server.user.AbstractUserInfoProvider;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserType;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;

@Named
@Singleton
public class SsoUserInfoProvider extends AbstractUserInfoProvider {
    
    @Inject
    public SsoUserInfoProvider(UserDao userDao) {
        super(userDao);
    }

    @Override
    public UserType getUserType() {
        return UserType.SSO;
    }

    @Override
    public UserInfo getInfo(UUID id, String username, String userDomain) {
        return getInfo(id, username, userDomain, UserType.LDAP);
    }

    @Override
    public UUID create(String username, String domain, String displayName, String email, Set<String> roles) {
        return create(username, domain, displayName, email, roles, UserType.LDAP);
    }
}
