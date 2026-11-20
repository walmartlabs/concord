package com.walmartlabs.concord.server.process;

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

import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.process.pipelines.processors.Chain;
import com.walmartlabs.concord.server.process.pipelines.processors.ProcessSubjectValidationProcessor;
import com.walmartlabs.concord.server.process.queue.ProcessInitiatorEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.security.ExternalIdentityDelegationManager;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import com.walmartlabs.concord.server.user.ExternalIdentityLifecycleManager;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.PrincipalCollection;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.walmartlabs.concord.server.process.ExternalProcessScopeManagerTest.principals;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProcessSecurityContextTest {

    @Test
    void trustedScopeIsRecheckedOnCacheHitAndDenialPreventsExecution() throws Exception {
        var state = mock(ProcessStateManager.class);
        var scope = mock(ExternalProcessScopeManager.class);
        var security = new ProcessSecurityContext(mock(SecurityManager.class), state, scope);
        var key = ProcessKey.random();
        var principals = principals(UUID.randomUUID(), UserType.LOCAL);
        when(state.<PrincipalCollection>get(eq((PartialProcessKey) key), eq(".concord/current_user"), any()))
                .thenReturn(Optional.of(principals));
        assertSame(principals, security.getPrincipals(key));
        doThrow(new UnauthorizedException("scope revoked")).when(scope).assertRestorationAllowed(key, principals);
        assertThrows(UnauthorizedException.class, () -> security.getPrincipals(key));
        var executed = new AtomicBoolean();
        assertThrows(UnauthorizedException.class, () -> security.runAsCurrentUser(key, () -> {
            executed.set(true);
            return null;
        }));
        verify(state, times(1)).get(eq((PartialProcessKey) key), eq(".concord/current_user"), any());
        verify(scope, times(3)).assertRestorationAllowed(key, principals);
        assertFalse(executed.get());
    }

    @Test
    void subjectReplacementCannotDiscardTrustedScope() {
        var state = mock(ProcessStateManager.class);
        var scope = mock(ExternalProcessScopeManager.class);
        var security = new ProcessSecurityContext(mock(SecurityManager.class), state, scope);
        var key = ProcessKey.random();
        var replacement = principals(UUID.randomUUID(), UserType.LOCAL);
        doThrow(new UnauthorizedException("scope mismatch")).when(scope).assertRestorationAllowed(key, replacement);
        assertThrows(UnauthorizedException.class, () -> security.storeSubject(key, replacement));
        verifyNoInteractions(state);
    }

    @Test
    void storingSubjectInvalidatesBothFullAndPartialKeyCacheEntries() {
        var state = mock(ProcessStateManager.class);
        var scope = mock(ExternalProcessScopeManager.class);
        var security = new ProcessSecurityContext(mock(SecurityManager.class), state, scope);
        var key = ProcessKey.random();
        var partial = PartialProcessKey.from(key.getInstanceId());
        var old = principals(UUID.randomUUID(), UserType.LOCAL);
        var replacement = principals(UUID.randomUUID(), UserType.LDAP);
        when(state.<PrincipalCollection>get(any(PartialProcessKey.class), eq(".concord/current_user"), any()))
                .thenReturn(Optional.of(old));
        assertSame(old, security.getPrincipals(key));
        assertSame(old, security.getPrincipals(partial));
        security.storeSubject(key, replacement);
        when(state.<PrincipalCollection>get(any(PartialProcessKey.class), eq(".concord/current_user"), any()))
                .thenReturn(Optional.of(replacement));
        assertSame(replacement, security.getPrincipals(key));
        assertSame(replacement, security.getPrincipals(partial));
        verify(state).replace(eq(key), eq(".concord/current_user"), any(byte[].class));
    }

    @Test
    void resumeValidationStopsChainBeforeIdentityChangesOrHooks() {
        var security = mock(ProcessSecurityContext.class);
        var payload = new Payload(ProcessKey.random());
        var executed = new AtomicBoolean();
        var chain = new Chain(new ProcessSubjectValidationProcessor(security), (next, p) -> {
            executed.set(true);
            return next.process(p);
        });
        when(security.getPrincipals(payload.getProcessKey())).thenThrow(new UnauthorizedException("scope mismatch"));
        assertThrows(UnauthorizedException.class, () -> chain.process(payload));
        assertFalse(executed.get());
    }

    @Test
    void sessionAuthenticationRechecksTrustedQueueEvenWithCachedAccountPrincipals() {
        var dao = mock(ExternalProcessScopeDao.class);
        var scope = new ExternalProcessScopeManager(dao, mock(ExternalIdentityLifecycleManager.class),
                mock(ExternalIdentityDelegationManager.class));
        var state = mock(ProcessStateManager.class);
        var security = new ProcessSecurityContext(mock(SecurityManager.class), state, scope);
        var queue = mock(ProcessQueueManager.class);
        var realm = new SessionKeyRealm(security, queue);
        var key = ProcessKey.random();
        var partial = PartialProcessKey.from(key.getInstanceId());
        var userId = UUID.randomUUID();
        var principals = principals(userId, UserType.LOCAL);
        when(state.<PrincipalCollection>get(eq(partial), eq(".concord/current_user"), any()))
                .thenReturn(Optional.of(principals));
        when(queue.getInitiator(partial)).thenReturn(ProcessInitiatorEntry.builder().instanceId(key.getInstanceId())
                .createdAt(key.getCreatedAt()).initiatorId(userId).status(ProcessStatus.RUNNING).build());
        when(dao.get(partial)).thenReturn(Optional.of(new ExternalProcessScopeDao.StoredContext(key, userId,
                UserType.LOCAL, null, null, null, null)));
        var account = realm.getAuthenticationInfo(new SessionKey(key.getInstanceId()));
        assertNotNull(account.getPrincipals().oneByType(SessionKeyPrincipal.class));
        when(dao.get(partial)).thenReturn(Optional.of(new ExternalProcessScopeDao.StoredContext(key, userId,
                UserType.EXTERNAL, null, null, null, null)));
        assertThrows(UnauthorizedException.class, () -> realm.getAuthenticationInfo(new SessionKey(key.getInstanceId())));
        verify(state, times(1)).get(eq(partial), eq(".concord/current_user"), any());
    }
}
