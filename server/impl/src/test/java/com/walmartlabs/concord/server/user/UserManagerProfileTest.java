package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.sdk.security.GroupRef;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.sdk.security.UserProfileService;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class UserManagerProfileTest {
    @Test
    void resolvesProfileAndGroupsForRequestedAccount() {
        UUID owner = UUID.randomUUID();
        UserProfileService profiles = mock(UserProfileService.class);
        AuthorizationService authorization = mock(AuthorizationService.class);
        when(profiles.get(owner)).thenReturn(new UserProfile("owner", "domain", "Owner", "owner@example.com", Map.of("department", "A")));
        var ldap = new GroupRef("ldap", "cn=staff");
        var other = new GroupRef("oidc", "staff");
        when(authorization.getGroups(owner)).thenReturn(Set.of(ldap, other));
        var manager = new UserManager(null, null, null, profiles, authorization, null, null, null);

        UserInfo info = manager.getInfo(owner);
        assertEquals(owner, info.id());
        assertEquals("owner", info.username());
        assertEquals(Set.of("cn=staff"), info.groups());
        assertEquals(Set.of(ldap, other), info.authorizationGroups());
        verify(profiles).get(owner);
        verify(authorization).getGroups(owner);
    }
}
