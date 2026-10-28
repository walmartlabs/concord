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

import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.authorization.AuthorizationService;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RunAsAuthorizationTest {
    private final DefaultSecurityManager manager = new DefaultSecurityManager();
    private final UserManager users = mock(UserManager.class);
    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final UUID id = UUID.randomUUID();
    private final UserEntry user = new UserEntry(id, "alice", "example", null, null, null, null, null, false, null, false);

    @AfterEach
    void cleanup() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void internalRunAsPreparesAndRestoresOuterSubject() throws Exception {
        when(users.get(id)).thenReturn(Optional.of(user));
        when(authorization.prepare(id, false)).thenReturn(new AuthorizationService.EffectiveAuthorization(Set.of(), Set.of()));
        ThreadContext.bind(manager);
        Subject outer = new Subject.Builder(manager).buildSubject();
        ThreadContext.bind(outer);
        UserSecurityContext context = new UserSecurityContext(manager, users, authorization);
        assertEquals(id, context.runAs(id, () -> {
            SubjectPrincipal current = SecurityUtils.assertCurrent(SubjectPrincipal.class);
            assertNotNull(AuthorizationScope.current(id));
            return current.userId();
        }));
        assertSame(outer, ThreadContext.getSubject());
        assertNull(AuthorizationScope.current(id));
        verify(authorization).prepare(id, false);
    }

    @Test
    void internalPreparationFailureNeverRunsCallable() {
        when(users.get(id)).thenReturn(Optional.of(user));
        when(authorization.prepare(id, false)).thenThrow(new IllegalStateException("unavailable"));
        AtomicBoolean called = new AtomicBoolean();
        UserSecurityContext context = new UserSecurityContext(manager, users, authorization);
        assertThrows(IllegalStateException.class, () -> context.runAs(id, () -> { called.set(true); return null; }));
        assertFalse(called.get());
        assertNull(AuthorizationScope.current(id));
    }

    @Test
    void permissionChecksPrepareEveryTimeAndPropagateFailures() {
        org.apache.shiro.mgt.SecurityManager securityManager = mock(org.apache.shiro.mgt.SecurityManager.class);
        when(users.get(id)).thenReturn(Optional.of(user));
        when(authorization.prepare(id, false))
                .thenReturn(new AuthorizationService.EffectiveAuthorization(Set.of(), Set.of()))
                .thenThrow(new IllegalStateException("unavailable"));
        when(securityManager.isPermitted(any(org.apache.shiro.subject.PrincipalCollection.class), eq(Permission.CREATE_ORG.getKey())))
                .thenReturn(true);
        UserSecurityContext context = new UserSecurityContext(securityManager, users, authorization);
        assertTrue(context.isPermitted(id, Permission.CREATE_ORG));
        assertThrows(IllegalStateException.class, () -> context.isPermitted(id, Permission.CREATE_ORG));
        verify(securityManager, times(1)).isPermitted(any(org.apache.shiro.subject.PrincipalCollection.class), eq(Permission.CREATE_ORG.getKey()));
        assertNull(AuthorizationScope.current(id));
    }

    @Test
    void processRunAsNormalizesLegacyAndPreparesBeforeWork() throws Exception {
        ProcessKey key = ProcessKey.random();
        ProcessSecurityContext context = spy(new ProcessSecurityContext(manager, mock(ProcessStateManager.class), authorization));
        UserPrincipal legacy = new UserPrincipal("oldPlugin", user);
        doReturn(new SimplePrincipalCollection(legacy, "oldPlugin")).when(context).getPrincipals(key);
        when(authorization.prepare(id, false)).thenReturn(new AuthorizationService.EffectiveAuthorization(Set.of(), Set.of()));
        assertEquals(id, context.runAsCurrentUser(key, () -> {
            assertSame(legacy, SecurityUtils.getCurrent(UserPrincipal.class));
            assertNotNull(AuthorizationScope.current(id));
            return SecurityUtils.assertCurrent(SubjectPrincipal.class).userId();
        }));
        verify(authorization).prepare(id, false);
    }

    @Test
    void processPreparationFailureNeverRunsCallable() {
        ProcessKey key = ProcessKey.random();
        ProcessSecurityContext context = spy(new ProcessSecurityContext(manager, mock(ProcessStateManager.class), authorization));
        doReturn(new SimplePrincipalCollection(new UserPrincipal("oldPlugin", user), "oldPlugin")).when(context).getPrincipals(key);
        when(authorization.prepare(id, false)).thenThrow(new IllegalStateException("unavailable"));
        AtomicBoolean called = new AtomicBoolean();
        assertThrows(IllegalStateException.class, () -> context.runAsCurrentUser(key, () -> { called.set(true); return null; }));
        assertFalse(called.get());
    }
}
