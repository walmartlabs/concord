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

import com.walmartlabs.concord.server.ConcordApplicationException;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.org.team.TeamManager;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.security.UserPrincipal;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;

import static com.walmartlabs.concord.server.user.UserInfoProducer.UserInfo;

@Named
public class UserManager {

    private final UserDao userDao;
    private final TeamDao teamDao;
    private final Map<UserType, UserInfoProducer> userInfoProducers;

    @Inject
    public UserManager(UserDao userDao, TeamDao teamDao, List<UserInfoProducer> producers) {
        this.userDao = userDao;
        this.teamDao = teamDao;

        this.userInfoProducers = new HashMap<>();
        producers.forEach(p -> this.userInfoProducers.put(p.getUserType(), p));
    }

    public UserEntry getOrCreate(String username, UserType type) {
        UserEntry user = userDao.getByName(username);
        if (user != null) {
            return user;
        }
        return create(username, null, null, type);
    }

    public Optional<UserEntry> get(UUID id) {
        return Optional.ofNullable(userDao.get(id));
    }

    public Optional<UUID> getId(String username) {
        UUID id = userDao.getId(username);
        return Optional.ofNullable(id);
    }

    public Optional<UserEntry> update(UUID userId, String displayName, String email, UserType userType, boolean isDisabled) {
        return Optional.ofNullable(userDao.update(userId, displayName, email, userType, isDisabled));
    }

    public UserEntry create(String username, String displayName, String email, UserType type) {
        if (type == null) {
            type = UserPrincipal.assertCurrent().getType();
        }

        UserInfo userInfo = getInfo(username, type);
        if (userInfo == null) {
            throw new ConcordApplicationException("User not found: " + username);
        }

        String dn = displayName != null ? displayName : userInfo.displayName();
        String em = email != null ? email : userInfo.email();
        UUID id = userDao.insert(username, dn, em, type);

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

    public UserInfo getInfo(String username, UserType type) {
        UserInfoProducer p = userInfoProducers.get(type);
        if (p == null) {
            return UserInfo.EMPTY;
        }

        return p.getInfo(username);
    }
}
