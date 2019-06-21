package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.user.UserInfoProvider.UserInfo;

@Named
public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;
    private final Map<UserType, UserInfoProvider> userInfoProviders;

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao, List<UserInfoProvider> providers) {
        this.userDao = userDao;
        this.teamDao = teamDao;

        this.userInfoProviders = new HashMap<>();
        providers.forEach(p -> this.userInfoProviders.put(p.getUserType(), p));
    }

    public UserEntry getOrCreate(String username, String userDomain, UserType type) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UUID id = userDao.getId(username, userDomain, type);
        if (id != null) {
            return userDao.get(id);
        }

        // TODO: remove me when all users migrated
        if (userDomain != null) {
            id = userDao.getId(username, null, type);
            if (id != null) {
                userDao.updateDomain(id, userDomain);
                return userDao.get(id);
            }
        }

        return create(username, userDomain, null, null, type, null);
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username, String userDomain, UserType type) {
        return Optional.ofNullable(userDao.getId(username, userDomain, type));
    }

    public Optional<UserEntry> update(UUID userId, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        return Optional.ofNullable(userDao.update(userId, displayName, email, userType, isDisabled, roles));
    }

    public UserEntry create(String username, String domain, String displayName, String email, UserType type, Set<String> roles) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserInfoProvider provider = assertProvider(type);
        UUID id = provider.create(username, domain, displayName, email, roles);

        // add the new user to the default org/team
        UUID teamId = TeamManager.DEFAULT_ORG_TEAM_ID;
        teamDao.upsertUser(teamId, id, TeamRole.MEMBER);

        return userDao.get(id);
    }

    public boolean isInOrganization(UUID orgId) {
        UserPrincipal p = UserPrincipal.assertCurrent();
        UUID userId = p.getId();
        return userDao.isInOrganization(userId, orgId);
    }

    public UserInfo getCurrentUserInfo() {
        UserPrincipal u = UserPrincipal.getCurrent();
        if (u == null) {
            return null;
        }

        UserInfoProvider p = assertProvider(u.getType());
        return p.getInfo(u.getId(), u.getUsername(), u.getDomain());
    }

    public UserInfo getInfo(String username, String domain, UserType type) {
        UserInfoProvider p = assertProvider(type);
        return p.getInfo(null, username, domain);
    }

    private UserInfoProvider assertProvider(UserType type) {
        UserInfoProvider p = userInfoProviders.get(type);
        if (p == null) {
            throw new ConcordApplicationException("Unknown user account type: " + type);
        }
        return p;
    }
}
