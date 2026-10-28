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

import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.cfg.AuditConfiguration;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.process.queue.ProcessInitiatorEntry;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.sdk.PartialProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.sdk.security.AuthenticationContext;
import com.walmartlabs.concord.server.sdk.security.SubjectPrincipal;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import com.walmartlabs.concord.server.security.github.GithubKey;
import com.walmartlabs.concord.server.security.github.GithubRealm;
import com.walmartlabs.concord.server.security.internal.InternalRealm;
import com.walmartlabs.concord.server.security.sessionkey.SessionKey;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyPrincipal;
import com.walmartlabs.concord.server.security.sessionkey.SessionKeyRealm;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoreRealmsTest {
    private static final UUID GITHUB_USER = UUID.fromString("acc17a02-b471-46af-9914-48cba3dd31ab");
    private final UserManager users = mock(UserManager.class);

    private static UserEntry user(UUID id, boolean disabled) {
        return new UserEntry(id, "alice", "example", null, null, null, null, null, disabled, null, false);
    }

    private ApiRealm apiRealm() {
        return new ApiRealm(users, new AuditLog(mock(AuditConfiguration.class), null, null));
    }

    @Test
    void apiKeysKeepCredentialAndRememberMeWithoutInventingUsers() {
        UUID id = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        when(users.get(id)).thenReturn(Optional.of(user(id, false)));
        ApiKey key = new ApiKey(keyId, id, "secret", true);
        AuthenticationInfo info = apiRealm().authenticate(key);
        SubjectPrincipal principal = info.getPrincipals().oneByType(SubjectPrincipal.class);
        assertEquals(id, principal.userId());
        assertEquals("apikey", principal.authentication().providerId());
        assertEquals(keyId.toString(), principal.authentication().credentialId());
        assertSame(key, info.getPrincipals().oneByType(ApiKey.class));
        assertTrue(info.getPrincipals().oneByType(ApiKey.class).isRememberMe());
        assertEquals("secret", info.getCredentials());
        assertNull(info.getPrincipals().oneByType(UserPrincipal.class));

        ApiKey userless = new ApiKey(keyId, null, "secret", true);
        AuthenticationInfo anonymous = apiRealm().authenticate(userless);
        assertNull(anonymous.getPrincipals().oneByType(SubjectPrincipal.class));
        assertSame(userless, anonymous.getPrincipals().oneByType(ApiKey.class));
    }

    @Test
    void apiKeyRejectsMissingAndDisabledUsers() {
        UUID id = UUID.randomUUID();
        ApiKey key = new ApiKey(UUID.randomUUID(), id, "secret", false);
        when(users.get(id)).thenReturn(Optional.empty(), Optional.of(user(id, true)));
        assertNull(apiRealm().authenticate(key));
        assertThrows(AuthenticationException.class, () -> apiRealm().authenticate(key));
    }

    @Test
    void githubRetainsSeededIdentityAndCredential() {
        when(users.get(GITHUB_USER)).thenReturn(Optional.of(user(GITHUB_USER, false)));
        GithubKey key = new GithubKey("secret", UUID.randomUUID(), "repo");
        AuthenticationInfo info = new GitRealm(users).authenticate(key);
        SubjectPrincipal principal = info.getPrincipals().oneByType(SubjectPrincipal.class);
        assertEquals(GITHUB_USER, principal.userId());
        assertEquals("github", principal.authentication().providerId());
        assertSame(key, info.getPrincipals().oneByType(GithubKey.class));
        assertEquals("secret", info.getCredentials());
        assertNull(info.getPrincipals().oneByType(UserPrincipal.class));
        assertFalse(new InternalRealm().supports(key));
    }

    @Test
    void sessionKeyUsesCurrentActorAndPreservesProcessScope() {
        UUID instanceId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        ProcessQueueManager queue = mock(ProcessQueueManager.class);
        ProcessSecurityContext context = mock(ProcessSecurityContext.class);
        ProcessInitiatorEntry process = mock(ProcessInitiatorEntry.class);
        when(queue.getInitiator(processKey)).thenReturn(process);
        when(process.initiatorId()).thenReturn(UUID.randomUUID());
        when(process.status()).thenReturn(ProcessStatus.RUNNING);
        SubjectPrincipal principal = new SubjectPrincipal(actor, "alice", "example", new AuthenticationContext("oldPlugin", null, null));
        when(context.getPrincipals(processKey)).thenReturn(new SimplePrincipalCollection(principal, "oldPlugin"));
        SessionRealm realm = new SessionRealm(context, queue);
        AuthenticationInfo info = realm.authenticate(new SessionKey(instanceId));
        assertEquals(actor, info.getPrincipals().oneByType(SubjectPrincipal.class).userId());
        assertNull(info.getPrincipals().oneByType(UserPrincipal.class));
        assertEquals(processKey, info.getPrincipals().oneByType(SessionKeyPrincipal.class).getProcessKey());
        assertEquals(instanceId, info.getCredentials());

        for (ProcessStatus status : new ProcessStatus[]{ProcessStatus.FINISHED, ProcessStatus.FAILED, ProcessStatus.CANCELLED, ProcessStatus.TIMED_OUT}) {
            when(process.status()).thenReturn(status);
            assertNull(realm.authenticate(new SessionKey(instanceId)));
        }
        verify(context, times(1)).getPrincipals(processKey);
    }

    @Test
    void sessionKeyRejectsMissingOrNestedProcessIdentity() {
        UUID instanceId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        PartialProcessKey processKey = PartialProcessKey.from(instanceId);
        ProcessQueueManager queue = mock(ProcessQueueManager.class);
        ProcessSecurityContext context = mock(ProcessSecurityContext.class);
        ProcessInitiatorEntry process = mock(ProcessInitiatorEntry.class);
        when(queue.getInitiator(processKey)).thenReturn(process);
        when(process.initiatorId()).thenReturn(actor);
        when(process.status()).thenReturn(ProcessStatus.RUNNING);
        SessionRealm realm = new SessionRealm(context, queue);
        assertThrows(AuthenticationException.class, () -> realm.authenticate(new SessionKey(instanceId)));

        SimplePrincipalCollection principals = new SimplePrincipalCollection(
                new SubjectPrincipal(actor, "alice", "example", new AuthenticationContext("oldPlugin", null, null)), "oldPlugin");
        principals.add(new SessionKeyPrincipal(processKey), SessionKeyRealm.REALM_NAME);
        when(context.getPrincipals(processKey)).thenReturn(principals);
        assertThrows(AuthenticationException.class, () -> realm.authenticate(new SessionKey(instanceId)));
    }

    private static class ApiRealm extends ApiKeyRealm {
        ApiRealm(UserManager users, AuditLog audit) { super(users, audit); }
        AuthenticationInfo authenticate(AuthenticationToken token) { return doGetAuthenticationInfo(token); }
    }

    private static class GitRealm extends GithubRealm {
        GitRealm(UserManager users) { super(users); }
        AuthenticationInfo authenticate(AuthenticationToken token) { return doGetAuthenticationInfo(token); }
    }

    private static class SessionRealm extends SessionKeyRealm {
        SessionRealm(ProcessSecurityContext context, ProcessQueueManager queue) { super(context, queue); }
        AuthenticationInfo authenticate(AuthenticationToken token) { return doGetAuthenticationInfo(token); }
    }
}
