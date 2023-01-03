package com.walmartlabs.concord.server.org;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.security.UserPrincipal;

import java.util.UUID;

public final class ResourceAccessUtils {

    public static UUID getTeamId(OrganizationDao orgDao, TeamDao teamDao, UUID baseOrgId, ResourceAccessEntry e) {
        UUID id = e.getTeamId();
        if (id != null) {
            return id;
        }

        if (e.getTeamName() == null) {
            throw new IllegalArgumentException("You must specify an organization and/or a team name.");
        }

        UUID orgId = baseOrgId;
        if (e.getOrgName() != null) {
            orgId = orgDao.getId(e.getOrgName());
        }

        if (orgId == null) {
            throw new IllegalArgumentException("Organization not found: " + e.getOrgName());
        }

        UUID teamId = teamDao.getId(orgId, e.getTeamName());
        if (teamId == null) {
            throw new IllegalArgumentException("Team not found: " + e.getTeamName());
        }

        return teamId;
    }

    public static boolean isSame(UserPrincipal p, EntityOwner owner) {
        if (p == null || owner == null) {
            return false;
        }

        UUID userId = p.getId();
        UUID ownerId = owner.id();
        return userId.equals(ownerId);
    }

    private ResourceAccessUtils() {
    }
}
