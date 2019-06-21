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
    public UserManager(UserDao userDao, TeamDao teamDao, List<UserInfoProvider> producers) {
        this.userDao = userDao;
        this.teamDao = teamDao;

        this.userInfoProviders = new HashMap<>();
        producers.forEach(p -> this.userInfoProviders.put(p.getUserType(), p));
    }

    public UserEntry getOrCreate(String username, UserType type) {
        UserEntry user = userDao.getByName(username);
        if (user != null) {
            return user;
        }
        return create(username, null, null, type, null);
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username) {
        UUID id = userDao.getId(username);
        return Optional.ofNullable(id);
    }

    public Optional<UserEntry> update(UUID userId, String displayName, String email, UserType userType, boolean isDisabled, Set<String> roles) {
        return Optional.ofNullable(userDao.update(userId, displayName, email, userType, isDisabled, roles));
    }

    public UserEntry create(String username, String displayName, String email, UserType type, Set<String> roles) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserInfo i = getInfo(username, type);

        String dn = displayName != null ? displayName : (i != null ? i.displayName() : null);
        String em = email != null ? email : (i != null ? i.email() : null);
        UUID id = userDao.insert(username, dn, em, type, roles);

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
        return p.getInfo(u.getId(), u.getUsername());
    }

    public UserInfo getInfo(String username, UserType type) {
        UserInfoProvider p = assertProvider(type);
        return p.getInfo(null, username);
    }

    private UserInfoProvider assertProvider(UserType type) {
        UserInfoProvider p = userInfoProviders.get(type);
        if (p == null) {
            throw new ConcordApplicationException("Unknown user account type: " + type);
        }
        return p;
    }
}
