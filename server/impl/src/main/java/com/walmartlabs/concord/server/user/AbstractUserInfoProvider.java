package com.walmartlabs.concord.server.user;

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

import java.util.Set;
import java.util.UUID;

public abstract class AbstractUserInfoProvider implements UserInfoProvider{
    
    private final UserDao userDao;
    
    public AbstractUserInfoProvider(UserDao userDao){
        this.userDao = userDao;
    }
    
    protected UserInfo getInfo(UUID id, String username, String userDomain, UserType type) {
        if (id == null) {
            id = userDao.getId(username, userDomain, type);
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

    protected UUID create(String username, String domain, String displayName, String email, Set<String> roles, UserType type) {
        return userDao.insertOrUpdate(username, domain, displayName, email, type, roles);
    }
}
