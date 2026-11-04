package com.walmartlabs.concord.server.security;

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

import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserSecurityContextTest {

    private final UserManager userManager = mock(UserManager.class);
    private final SecurityManager securityManager = mock(SecurityManager.class);
    private final UserSecurityContext ctx = new UserSecurityContext(securityManager, userManager);

    @Test
    public void userlessApiKeyIsGrantedUserlessPermissions() {
        assertTrue(ctx.isPermitted(null, Permission.AGENT_WEBSOCKET));
    }

    @Test
    public void userlessApiKeyIsDeniedOtherPermissions() {
        assertFalse(ctx.isPermitted(null, Permission.CREATE_ORG));
    }

    @Test
    public void unknownUserIsDenied() {
        UUID userId = UUID.randomUUID();
        when(userManager.get(userId)).thenReturn(Optional.empty());

        assertFalse(ctx.isPermitted(userId, Permission.AGENT_WEBSOCKET));
    }

    @Test
    public void knownUserPermissionIsDelegatedToSecurityManager() {
        UUID userId = UUID.randomUUID();
        UserEntry user = mock(UserEntry.class);
        when(userManager.get(userId)).thenReturn(Optional.of(user));
        when(securityManager.isPermitted(any(PrincipalCollection.class), any(String.class))).thenReturn(true);

        assertTrue(ctx.isPermitted(userId, Permission.AGENT_WEBSOCKET));
    }
}
