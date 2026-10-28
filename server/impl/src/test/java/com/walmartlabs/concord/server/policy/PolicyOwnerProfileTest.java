package com.walmartlabs.concord.server.policy;

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

import com.walmartlabs.concord.server.sdk.security.ExternalIdentityKey;
import com.walmartlabs.concord.server.sdk.security.UserProfile;
import com.walmartlabs.concord.server.security.identity.IdentityDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserInfo;
import com.walmartlabs.concord.server.user.UserManager;
import com.walmartlabs.concord.server.user.UserType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PolicyOwnerProfileTest {
    @Test
    void ownerProfileIsSelectedByUuidAndCannotOverwriteIdentity() {
        UUID id = UUID.randomUUID();
        UUID identityId = UUID.randomUUID();
        UserEntry owner = mock(UserEntry.class);
        when(owner.getId()).thenReturn(id);
        when(owner.getType()).thenReturn(UserType.LDAP);
        UserManager users = mock(UserManager.class);
        when(users.getInfo(id)).thenReturn(UserInfo.builder().id(id).username("owner").email("owner@example.com")
                .attributes(Map.of("id", "spoof", "username", "spoof", "userType", "spoof", "profileProviderId", "spoof", "department", "A")).build());
        var identity = new ExternalIdentityKey("ldap", "", "subject");
        when(users.profileSelection(id)).thenReturn(new IdentityDao.ProfileSelection(
                new UserProfile("owner", null, null, null, Map.of()), "ldap", identityId, identity));

        var attrs = new PolicyManager(null, null, users).getOwnerAttrs(owner);
        assertEquals(id, attrs.get("id"));
        assertEquals("owner", attrs.get("username"));
        assertEquals("LDAP", attrs.get("userType"));
        assertEquals("ldap", attrs.get("profileProviderId"));
        assertEquals(identityId, attrs.get("profileIdentityId"));
        assertEquals(identity, attrs.get("identity"));
        assertEquals("A", attrs.get("department"));
        verify(users).getInfo(id);
    }
}
