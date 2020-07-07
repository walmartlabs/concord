package com.walmartlabs.concord.server.security.internal;

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

import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfoProvider;
import com.walmartlabs.concord.server.user.UserType;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Set;
import java.util.UUID;

@Named
@Singleton
public class LocalUserInfoProvider implements UserInfoProvider {

    private final UserDao userDao;

    @Inject
    public LocalUserInfoProvider(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public UserType getUserType() {
        return UserType.LOCAL;
    }

    @Override
    public UserInfo getInfo(UUID id, String username, String userDomain) {
        if (id == null) {
            id = userDao.getId(username, userDomain, UserType.LOCAL);
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

    @Override
    public UUID create(String username, String domain, String displayName, String email, Set<String> roles) {
        return userDao.insertOrUpdate(username, domain, displayName, email, UserType.LOCAL, roles);
    }
}
