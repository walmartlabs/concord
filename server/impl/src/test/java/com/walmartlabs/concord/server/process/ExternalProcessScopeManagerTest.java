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

import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.security.ExternalIdentityDelegationManager;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.user.ExternalIdentityLifecycleManager;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserType;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalProcessScopeManagerTest {

    private final ProcessKey key = ProcessKey.random();
    private final ExternalProcessScope scope = new ExternalProcessScope(UUID.randomUUID(),
            new ExternalIdentityRef("provider", "authority", "app"), UUID.randomUUID(),
            UUID.randomUUID(), UUID.randomUUID());
    private final ExternalProcessScopeDao dao = mock(ExternalProcessScopeDao.class);
    private final ExternalIdentityLifecycleManager lifecycle = mock(ExternalIdentityLifecycleManager.class);
    private final ExternalIdentityDelegationManager delegation = mock(ExternalIdentityDelegationManager.class);
    private final ExternalProcessScopeManager manager = new ExternalProcessScopeManager(dao, lifecycle, delegation);

    @BeforeEach
    void setUp() {
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, scope.userId(), scope.projectId(),
                scope.repositoryId(), null)));
    }

    @Test
    void ordinaryAccountsAndProcessesWithoutQueueRowsRetainExistingBehavior() {
        for (var type : new UserType[]{UserType.LOCAL, UserType.LDAP}) {
            when(dao.get(key)).thenReturn(Optional.of(stored(type, null, scope.userId(), scope.projectId(), null, null)));
            assertDoesNotThrow(() -> manager.assertRestorationAllowed(key, principals(scope.userId(), type)));
        }
        when(dao.get(key)).thenReturn(Optional.empty());
        assertDoesNotThrow(() -> manager.assertRestorationAllowed(key, principals(scope.userId(), UserType.LOCAL)));
        verifyNoInteractions(lifecycle, delegation);
    }

    @Test
    void unscopedExternalQueueCannotBeLaunderedThroughSavedLocalAccount() {
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, null, scope.userId(),
                scope.projectId(), scope.repositoryId(), null)));
        assertDenied(principals(UUID.randomUUID(), UserType.LOCAL), "no trusted delegation scope");
        verifyNoInteractions(lifecycle, delegation);
    }

    @Test
    void externalSavedSubjectCannotUseMissingQueueOrUnscopedLocalQueue() {
        when(dao.get(key)).thenReturn(Optional.empty());
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "no trusted delegation scope");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.LOCAL, null, scope.userId(),
                scope.projectId(), null, null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "no trusted delegation scope");
    }

    @Test
    void alteredOrMissingSavedSubjectCannotReplaceTrustedIdentity() {
        assertDenied(principals(UUID.randomUUID(), UserType.EXTERNAL), "does not match");
        assertDenied(principals(scope.userId(), UserType.LOCAL), "does not match");
        assertDenied(new SimplePrincipalCollection(), "does not match");
        assertDenied(null, "does not match");
        var multiple = new SimplePrincipalCollection(principals(scope.userId(), UserType.EXTERNAL));
        multiple.add(principals(UUID.randomUUID(), UserType.EXTERNAL).getPrimaryPrincipal(), "test");
        assertDenied(multiple, "does not match");
        verifyNoInteractions(lifecycle, delegation);
    }

    @Test
    void changedQueueIntegrationOrAccountCannotRestoreScope() {
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.LOCAL, scope, scope.userId(),
                scope.projectId(), scope.repositoryId(), null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "does not match");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, UUID.randomUUID(),
                scope.projectId(), scope.repositoryId(), null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "does not match");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, scope.userId(),
                UUID.randomUUID(), scope.repositoryId(), null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "does not match");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, scope.userId(),
                scope.projectId(), UUID.randomUUID(), null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "does not match");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, scope.userId(),
                scope.projectId(), null, null)));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "does not match");
        verifyNoInteractions(lifecycle, delegation);
    }

    @Test
    void lifecycleFailureAndRecreatedIdentityAreDenied() throws Exception {
        when(lifecycle.requireActive(scope.identity())).thenThrow(new ExternalIdentityException("refresh failed"));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "refresh failed");
        reset(lifecycle);
        when(lifecycle.requireActive(scope.identity())).thenReturn(UUID.randomUUID());
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "binding changed");
        verifyNoInteractions(delegation);
    }

    @Test
    void removedSourceIntegrationIsDeniedBeforeLifecycleLookup() {
        doThrow(new UnauthorizedException("integration changed")).when(dao).assertSourceIntegration(scope);
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "integration changed");
        verifyNoInteractions(lifecycle, delegation);
    }

    @Test
    void policyRevocationIsRecheckedAndNeverReplacedBySavedGrants() throws Exception {
        when(lifecycle.requireActive(scope.identity())).thenReturn(scope.userId());
        when(delegation.requirePolicy(scope.identity(), scope.orgId(), scope.projectId(), scope.repositoryId()))
                .thenThrow(new UnauthorizedException("policy revoked"));
        assertDenied(principals(scope.userId(), UserType.EXTERNAL), "policy revoked");
    }

    @Test
    void eligibleRootAndChildStillCannotExecuteAndRecheckEveryTime() throws Exception {
        when(lifecycle.requireActive(scope.identity())).thenReturn(scope.userId());
        var principals = principals(scope.userId(), UserType.EXTERNAL);
        assertDenied(principals, "execution is disabled");
        when(dao.get(key)).thenReturn(Optional.of(stored(UserType.EXTERNAL, scope, scope.userId(),
                scope.projectId(), null, UUID.randomUUID())));
        assertDenied(principals, "execution is disabled");
        verify(lifecycle, times(2)).requireActive(scope.identity());
        verify(delegation, times(2)).requirePolicy(scope.identity(), scope.orgId(), scope.projectId(), scope.repositoryId());
        verify(dao, times(2)).assertSourceIntegration(scope);
    }

    private void assertDenied(PrincipalCollection principals, String message) {
        assertTrue(assertThrows(UnauthorizedException.class, () -> manager.assertRestorationAllowed(key, principals))
                .getMessage().contains(message));
    }

    private ExternalProcessScopeDao.StoredContext stored(UserType type, ExternalProcessScope scope,
                                                          UUID initiatorId, UUID projectId, UUID repositoryId, UUID parentId) {
        return new ExternalProcessScopeDao.StoredContext(key, initiatorId, type, projectId, repositoryId, parentId, scope);
    }

    static PrincipalCollection principals(UUID id, UserType type) {
        var user = new UserEntry(id, "test", null, null, null, type, null, null, false, null, false);
        return new SimplePrincipalCollection(new UserPrincipal("test", user), "test");
    }
}
