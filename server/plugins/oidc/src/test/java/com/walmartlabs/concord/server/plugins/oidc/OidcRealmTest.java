package com.walmartlabs.concord.server.plugins.oidc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import com.walmartlabs.concord.server.org.team.TeamEntry;
import com.walmartlabs.concord.server.org.team.TeamRole;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.Source;
import com.walmartlabs.concord.server.plugins.oidc.PluginConfiguration.TeamMapping;
import com.walmartlabs.concord.server.role.RoleDao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.smallrye.common.constraint.Assert.assertFalse;
import static io.smallrye.common.constraint.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class OidcRealmTest {

    @Test
    public void invalidRolesAreAllowed() {
        var roleDao = mock(RoleDao.class);
        when(roleDao.getId(eq("roleBaz"))).thenReturn(UUID.randomUUID());

        var input = Map.of(
                "roleFoo", List.of(new Source("groups", ".*")),
                "roleBar", List.of(new Source("groups", ".*")),
                "roleBaz", List.of(new Source("groups", ".*")));

        var output = OidcRealm.validateRoleMapping(input, roleDao);
        assertTrue(output.containsKey("roleFoo"));
        assertTrue(output.containsKey("roleBar"));
        assertTrue(output.containsKey("roleBaz"));
    }

    @Test
    public void invalidTeamsAreIgnored() {
        var teamIdFoo = UUID.randomUUID();
        var teamIdBar = UUID.randomUUID();
        var teamIdBaz = UUID.randomUUID();

        var teamDao = mock(TeamDao.class);
        when(teamDao.get(eq(teamIdBar))).thenReturn(new TeamEntry(teamIdBar, UUID.randomUUID(), "bar", "bar", "bar"));

        var input = Map.of(
                teamIdFoo, new TeamMapping(List.of(new Source("group", ".*")), TeamRole.MEMBER),
                teamIdBar, new TeamMapping(List.of(new Source("group", ".*")), TeamRole.MEMBER),
                teamIdBaz, new TeamMapping(List.of(new Source("group", ".*")), TeamRole.MEMBER));

        var output = OidcRealm.validateTeamMapping(input, teamDao);
        assertFalse(output.containsKey(teamIdFoo));
        assertTrue(output.containsKey(teamIdBar));
        assertFalse(output.containsKey(teamIdBaz));
    }
}
