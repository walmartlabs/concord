package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2026 Walmart Inc.
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

import com.walmartlabs.concord.common.secret.HashAlgorithm;
import com.walmartlabs.concord.common.secret.SecretEncryptedByType;
import com.walmartlabs.concord.common.secret.SecretUtils;
import com.walmartlabs.concord.server.audit.AuditAction;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.audit.AuditObject;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope;
import com.walmartlabs.concord.server.org.secret.provider.SecretStoreProvider;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.security.apikey.ApiKeyEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecretManagerProjectScopeTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();
    private static final UUID OTHER_PROJECT_ID = UUID.randomUUID();
    private static final UUID SECRET_ID = UUID.randomUUID();
    private static final String SECRET_NAME = "cron-api-key";
    private static final String TOKEN = "synthetic-api-key-for-unit-tests-only";
    private static final byte[] SERVER_PASSWORD = "synthetic-server-password".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SALT = new byte[16];

    private SecretDao secretDao;
    private SecretStoreProvider stores;
    private SecretStore store;
    private ApiKeyDao apiKeyDao;
    private AuditLog auditLog;
    private AuditLog.EntryBuilder auditEntry;
    private OrganizationManager orgManager;
    private Subject subject;
    private SecretManager manager;

    @BeforeEach
    void setUp() {
        secretDao = mock(SecretDao.class);
        stores = mock(SecretStoreProvider.class);
        store = mock(SecretStore.class);
        apiKeyDao = mock(ApiKeyDao.class);
        auditLog = mock(AuditLog.class);
        auditEntry = mock(AuditLog.EntryBuilder.class, RETURNS_SELF);
        orgManager = mock(OrganizationManager.class);
        var cfg = mock(SecretStoreConfiguration.class);
        when(cfg.getServerPwd()).thenReturn(SERVER_PASSWORD);
        when(stores.getSecretStore("test")).thenReturn(store);
        when(auditLog.add(AuditObject.SECRET, AuditAction.ACCESS)).thenReturn(auditEntry);
        manager = new SecretManager(null, auditLog, orgManager, null, secretDao, cfg, stores,
                null, null, null, null, apiKeyDao);

        subject = mock(Subject.class);
        var user = new UserEntry(UUID.randomUUID(), "cron", null, null, null, null, null, null, false, null, false);
        var principal = new UserPrincipal("test", user);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(principal, "test"));
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(true);
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
    }

    @Test
    void runAsRejectsPrivateSecretFromAnotherProjectBeforeReadingData() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID));

        assertThrows(UnauthorizedException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    @Test
    void runAsRejectsUnscopedPrivateSecretBeforeReadingData() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY));

        assertThrows(UnauthorizedException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    @Test
    void runAsResolvesMatchingProjectAndReturnsApiKeyOwner() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, PROJECT_ID));
        encrypt(null);
        var key = apiKey();

        var actual = manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null);

        assertSame(key, actual);
        assertEquals(key.getUserId(), actual.getUserId());
        verify(secretDao).getByName(ORG_ID, SECRET_NAME);
        verify(store).get(SECRET_ID);
        verify(apiKeyDao).find(TOKEN);
        verify(auditEntry).field("secretId", SECRET_ID);
        verify(auditEntry).log();
    }

    @Test
    void runAsAcceptsExplicitDelegationToMultipleProjects() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID, PROJECT_ID));
        encrypt(null);
        var key = apiKey();

        assertSame(key, manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));
    }

    @Test
    void runAsRechecksScopeWhenDelegationIsRemovedOrReassigned() {
        var allowed = secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, PROJECT_ID);
        var removed = secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY);
        var reassigned = secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID);
        when(secretDao.getByName(ORG_ID, SECRET_NAME)).thenReturn(allowed, removed, reassigned);
        encrypt(null);
        var key = apiKey();

        assertSame(key, manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));
        assertThrows(UnauthorizedException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));
        assertThrows(UnauthorizedException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        verify(secretDao, times(3)).getByName(ORG_ID, SECRET_NAME);
        verify(store).get(SECRET_ID);
        verify(apiKeyDao).find(TOKEN);
        verify(auditEntry).log();
    }

    @Test
    void runAsRejectsNonDataSecretBeforeDecryption() {
        resolve(secret(SecretType.KEY_PAIR, SecretEncryptedByType.SERVER_KEY, PROJECT_ID));

        assertThrows(IllegalArgumentException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    @Test
    void runAsRequiresPasswordBeforeReadingPasswordProtectedData() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.PASSWORD, PROJECT_ID));

        assertThrows(SecurityException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    @Test
    void runAsRejectsWrongPasswordWithoutApiLookupOrAccessAudit() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.PASSWORD, PROJECT_ID));
        encrypt("correct-password");

        assertThrows(SecurityException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, "wrong-password"));

        verify(store).get(SECRET_ID);
        verifyNoInteractions(apiKeyDao, auditLog);
    }

    @Test
    void runAsAcceptsCorrectPassword() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.PASSWORD, PROJECT_ID));
        encrypt("correct-password");
        var key = apiKey();

        assertSame(key, manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, "correct-password"));
    }

    @Test
    void runAsRejectsMissingApiKeyAfterAuditingSecretAccess() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, PROJECT_ID));
        encrypt(null);

        var error = assertThrows(ConcordApplicationException.class,
                () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));

        assertEquals(404, error.getResponse().getStatus());
        verify(apiKeyDao).find(TOKEN);
        verify(auditEntry).log();
    }

    @Test
    void explicitProjectScopeCannotBeBypassedByGlobalReader() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID));

        assertThrows(UnauthorizedException.class, () -> manager.getRaw(AccessScope.project(PROJECT_ID), ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    @Test
    void explicitMatchingProjectScopeStillDecrypts() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, PROJECT_ID));
        encrypt(null);

        assertArrayEquals(TOKEN.getBytes(StandardCharsets.UTF_8),
                manager.getRaw(AccessScope.project(PROJECT_ID), ORG_ID, SECRET_NAME, null).getData());
    }

    @Test
    void genericReadsStillAllowUnscopedSecrets() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY));
        encrypt(null);

        for (var scope : new AccessScope[]{AccessScope.project(PROJECT_ID), AccessScope.apiRequest(), AccessScope.internal()}) {
            assertArrayEquals(TOKEN.getBytes(StandardCharsets.UTF_8),
                    manager.getRaw(scope, ORG_ID, SECRET_NAME, null).getData());
        }
    }

    @Test
    void apiRequestStillAllowsGlobalReaderToReadProjectScopedSecret() {
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID));
        encrypt(null);

        assertArrayEquals(TOKEN.getBytes(StandardCharsets.UTF_8),
                manager.getRaw(AccessScope.apiRequest(), ORG_ID, SECRET_NAME, null).getData());
    }

    @Test
    void internalScopeStillAllowsAuthorizedNonGlobalReader() {
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(false);
        when(orgManager.assertAccess(ORG_ID, false)).thenReturn(mock(OrganizationEntry.class));
        when(secretDao.hasAccessLevel(eq(SECRET_ID), any(), any())).thenReturn(true);
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, OTHER_PROJECT_ID));
        encrypt(null);

        assertArrayEquals(TOKEN.getBytes(StandardCharsets.UTF_8),
                manager.getRaw(AccessScope.internal(), ORG_ID, SECRET_NAME, null).getData());
    }

    @Test
    void privateAclStillAppliesToRunAsAndGenericReads() {
        when(subject.hasRole(Roles.SYSTEM_READER)).thenReturn(false);
        when(orgManager.assertAccess(ORG_ID, false)).thenReturn(mock(OrganizationEntry.class));
        resolve(secret(SecretType.DATA, SecretEncryptedByType.SERVER_KEY, PROJECT_ID));

        assertThrows(UnauthorizedException.class, () -> manager.assertApiKey(PROJECT_ID, ORG_ID, SECRET_NAME, null));
        assertThrows(UnauthorizedException.class, () -> manager.getRaw(AccessScope.project(PROJECT_ID), ORG_ID, SECRET_NAME, null));
        assertThrows(UnauthorizedException.class, () -> manager.getRaw(AccessScope.internal(), ORG_ID, SECRET_NAME, null));

        verifyNoInteractions(stores, store, apiKeyDao, auditLog);
    }

    private SecretEntryV2 secret(SecretType type, SecretEncryptedByType encryptedBy, UUID... projectIds) {
        var projects = Arrays.stream(projectIds).map(id -> {
            var project = mock(ProjectEntry.class);
            when(project.getId()).thenReturn(id);
            return project;
        }).collect(Collectors.toSet());
        return new SecretEntryV2(SECRET_ID, SECRET_NAME, ORG_ID, "org", projects, type, encryptedBy,
                "test", SecretVisibility.PRIVATE, null, null, null, SALT, HashAlgorithm.SHA256);
    }

    private void resolve(SecretEntryV2 secret) {
        when(secretDao.getByName(ORG_ID, SECRET_NAME)).thenReturn(secret);
    }

    private void encrypt(String password) {
        var pwd = password == null ? SERVER_PASSWORD : password.getBytes(StandardCharsets.UTF_8);
        when(store.get(SECRET_ID)).thenReturn(SecretUtils.encrypt(TOKEN.getBytes(StandardCharsets.UTF_8), pwd, SALT, HashAlgorithm.SHA256));
    }

    private ApiKeyEntry apiKey() {
        var key = mock(ApiKeyEntry.class);
        when(key.getUserId()).thenReturn(UUID.randomUUID());
        when(apiKeyDao.find(TOKEN)).thenReturn(key);
        return key;
    }
}
