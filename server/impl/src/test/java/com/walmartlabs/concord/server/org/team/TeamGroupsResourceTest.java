package com.walmartlabs.concord.server.org.team;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.sdk.validation.ValidationErrorsException;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TeamGroupsResourceTest {
    private final TeamManager manager = mock(TeamManager.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final TeamGroupsResource resource = new TeamGroupsResource(mock(TeamDao.class), manager,
            mock(OrganizationManager.class), authorization);

    @Test
    void replacementIsConfinedToSelectedSource() {
        when(authorization.isActiveSource("oidc")).thenReturn(true);
        List<TeamSourceGroupEntry> groups = List.of(new TeamSourceGroupEntry("staff", TeamRole.MAINTAINER));
        resource.put("acme", "team", "oidc", true, groups);
        verify(manager).addGroups("acme", "team", "oidc", true, groups);
        verify(manager, never()).addGroups("acme", "team", "ldap", true, groups);
    }

    @Test
    void rejectsInvalidRoleAndInactiveSource() {
        when(authorization.isActiveSource("oidc")).thenReturn(true);
        assertThrows(ValidationErrorsException.class, () -> resource.put("acme", "team", "oidc", false,
                List.of(new TeamSourceGroupEntry("staff", null))));
        assertThrows(ValidationErrorsException.class, () -> resource.put("acme", "team", "missing", true, List.of()));
        verifyNoInteractions(manager);
    }
}
