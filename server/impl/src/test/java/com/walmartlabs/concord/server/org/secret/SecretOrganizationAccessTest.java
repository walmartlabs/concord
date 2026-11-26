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
import com.walmartlabs.concord.db.AbstractDao.TxResult;
import com.walmartlabs.concord.server.Locks;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.EntityOwner;
import com.walmartlabs.concord.server.org.OrganizationDao;
import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.org.OrganizationManager;
import com.walmartlabs.concord.server.org.OrganizationVisibility;
import com.walmartlabs.concord.server.org.ResourceAccessLevel;
import com.walmartlabs.concord.server.org.project.ProjectAccessManager;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryDao;
import com.walmartlabs.concord.server.org.secret.SecretManager.AccessScope;
import com.walmartlabs.concord.server.org.secret.provider.SecretStoreProvider;
import com.walmartlabs.concord.server.org.secret.store.SecretStore;
import com.walmartlabs.concord.server.org.team.TeamDao;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.queue.ProcessQueueManager;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.apikey.ApiKeyDao;
import com.walmartlabs.concord.server.user.UserDao;
import com.walmartlabs.concord.server.user.UserEntry;
import com.walmartlabs.concord.server.user.UserManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.Tables.SECRETS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SecretOrganizationAccessTest {

    private final UUID orgId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID orgOwnerId = UUID.randomUUID();
    private final DSLContext tx = mock(DSLContext.class);
    private final OrganizationDao orgDao = mock(OrganizationDao.class);
    private final UserManager userManager = mock(UserManager.class);
    private final UserDao userDao = mock(UserDao.class);
    private final SecretDao secretDao = mock(SecretDao.class);
    private final SecretStoreProvider stores = mock(SecretStoreProvider.class);
    private final SecretStore store = mock(SecretStore.class);
    private final Subject subject = mock(Subject.class);
    private OrganizationManager orgManager;
    private SecretManager secrets;
    private Subject previousSubject;
    private SecurityManager previousSecurityManager;

    @BeforeEach
    void setUp() {
        previousSubject = ThreadContext.getSubject();
        previousSecurityManager = ThreadContext.getSecurityManager();
        ThreadContext.bind(mock(SecurityManager.class));
        ThreadContext.bind(subject);
        var user = new UserEntry(userId, "test-user", null, null, null, null, null, null, false, null, false);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(new UserPrincipal("test", user), "test"));
        when(orgDao.txResult(any())).thenAnswer(invocation -> {
            var action = invocation.<TxResult<?>>getArgument(0);
            return action.run(tx);
        });
        organization(OrganizationVisibility.PRIVATE, orgOwnerId);
        orgManager = new OrganizationManager(mock(PolicyManager.class), orgDao, mock(TeamDao.class),
                userManager, mock(Locks.class), mock(AuditLog.class));
        secrets = new SecretManager(mock(PolicyManager.class), mock(AuditLog.class, RETURNS_DEEP_STUBS),
                orgManager, mock(ProcessQueueManager.class), secretDao, mock(SecretStoreConfiguration.class),
                stores, userDao, mock(ProjectAccessManager.class), mock(RepositoryDao.class),
                userManager, mock(ApiKeyDao.class));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unbindSubject();
        ThreadContext.unbindSecurityManager();
        if (previousSubject != null) {
            ThreadContext.bind(previousSubject);
        }
        if (previousSecurityManager != null) {
            ThreadContext.bind(previousSecurityManager);
        }
    }

    @Test
    void outsiderCannotListPrivateOrganizationSecrets() {
        assertThrows(UnauthorizedException.class, () -> secrets.list(orgId, 0, 10, null));

        verify(userManager).isInOrganization(tx, orgId);
        verifyNoInteractions(secretDao, stores, store);
    }

    @Test
    void outsiderCannotReadPublicSecretMetadataInPrivateOrganization() {
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of());

        assertThrows(UnauthorizedException.class, () -> read(secret));

        verify(secretDao).getByName(orgId, secret.getName());
        verify(userManager).isInOrganization(tx, orgId);
        verifyNoMoreInteractions(secretDao);
        verifyNoInteractions(stores, store);
    }

    @Test
    void idLookupChecksActualOrganizationRatherThanSuppliedOrganization() {
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of());
        var suppliedOrgId = UUID.randomUUID();
        when(orgDao.get(tx, suppliedOrgId)).thenReturn(new OrganizationEntry(suppliedOrgId, "public-org",
                null, OrganizationVisibility.PUBLIC, null, null));

        assertThrows(UnauthorizedException.class, () -> secrets.assertAccess(suppliedOrgId, secret.getId(),
                null, ResourceAccessLevel.READER, false));

        verify(orgDao).get(tx, orgId);
        verify(orgDao, never()).get(tx, suppliedOrgId);
        verify(secretDao).get(secret.getId());
        verifyNoMoreInteractions(secretDao);
    }

    @Test
    void formerMemberCannotUseSecretOwnershipToBypassPrivateOrganization() {
        var secret = secret(SecretVisibility.PUBLIC, userId, Set.of());

        assertThrows(UnauthorizedException.class, () -> read(secret));
        assertThrows(UnauthorizedException.class,
                () -> secrets.getRaw(AccessScope.internal(), orgId, secret.getName(), "test-only"));

        verify(userManager, times(2)).isInOrganization(tx, orgId);
        verifyNoInteractions(stores, store);
    }

    @Test
    void allDataAccessScopesRejectOutsiderBeforeFetchingEncryptedBytes() {
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of());
        var scopes = List.of(AccessScope.apiRequest(), AccessScope.internal(), AccessScope.project(UUID.randomUUID()));

        for (var scope : scopes) {
            assertThrows(UnauthorizedException.class,
                    () -> secrets.getRaw(scope, orgId, secret.getName(), "test-only"), scope.getName());
        }

        verify(userManager, times(scopes.size())).isInOrganization(tx, orgId);
        verify(secretDao, times(scopes.size())).getByName(orgId, secret.getName());
        verifyNoMoreInteractions(secretDao);
        verifyNoInteractions(stores, store);
    }

    @Test
    void memberCanReadAndListPublicSecretAndAuthorizationPrecedesByteAccess() {
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of());
        when(userManager.isInOrganization(tx, orgId)).thenReturn(true);
        var data = "synthetic-test-data".getBytes(StandardCharsets.UTF_8);
        when(stores.getSecretStore("test")).thenReturn(store);
        when(store.get(secret.getId())).thenReturn(SecretUtils.encrypt(data,
                "test-only".getBytes(StandardCharsets.UTF_8), secret.getSecretSalt(), HashAlgorithm.SHA256));
        when(secretDao.list(orgId, userId, SECRETS.SECRET_NAME, true, 2, 5, "test"))
                .thenReturn(List.of(secret));

        assertArrayEquals(data, secrets.getRaw(AccessScope.apiRequest(), orgId, secret.getName(), "test-only").getData());
        assertEquals(List.of(secret), secrets.list(orgId, 2, 5, "test"));

        var order = inOrder(secretDao, orgDao, userManager, stores, store);
        order.verify(secretDao).getByName(orgId, secret.getName());
        order.verify(orgDao).get(tx, orgId);
        order.verify(userManager).isInOrganization(tx, orgId);
        order.verify(stores).getSecretStore("test");
        order.verify(store).get(secret.getId());
        order.verify(orgDao).get(tx, orgId);
        order.verify(userManager).isInOrganization(tx, orgId);
        order.verify(secretDao).list(orgId, userId, SECRETS.SECRET_NAME, true, 2, 5, "test");
    }

    @Test
    void membershipDoesNotReplacePrivateSecretAcl() {
        var secret = secret(SecretVisibility.PRIVATE, UUID.randomUUID(), Set.of());
        when(userManager.isInOrganization(tx, orgId)).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> read(secret));
        assertThrows(UnauthorizedException.class,
                () -> secrets.getRaw(AccessScope.internal(), orgId, secret.getName(), "test-only"));

        verify(secretDao, times(2)).hasAccessLevel(eq(secret.getId()), eq(userId), any());
        verifyNoInteractions(stores, store);
        when(secretDao.hasAccessLevel(eq(secret.getId()), eq(userId), any())).thenReturn(true);
        assertSame(secret, read(secret));
    }

    @Test
    void memberStillNeedsPasswordAndMatchingProjectBeforeByteAccess() {
        var project = mock(ProjectEntry.class);
        var projectId = UUID.randomUUID();
        when(project.getId()).thenReturn(projectId);
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of(project));
        when(userManager.isInOrganization(tx, orgId)).thenReturn(true);

        assertThrows(UnauthorizedException.class, () -> secrets.getRaw(AccessScope.project(UUID.randomUUID()),
                orgId, secret.getName(), "test-only"));
        assertThrows(SecurityException.class,
                () -> secrets.getRaw(AccessScope.project(projectId), orgId, secret.getName(), null));

        verifyNoInteractions(stores, store);
    }

    @Test
    void publicOrganizationStillAllowsOutsiderPublicSecretReadAndList() {
        organization(OrganizationVisibility.PUBLIC, orgOwnerId);
        var secret = secret(SecretVisibility.PUBLIC, UUID.randomUUID(), Set.of());

        assertSame(secret, read(secret));
        secrets.list(orgId, 0, 10, null);

        verifyNoInteractions(userManager);
        verify(secretDao).list(orgId, userId, SECRETS.SECRET_NAME, true, 0, 10, null);
    }

    @Test
    void organizationOwnerRetainsAccessWithoutMembershipOrSecretAcl() {
        organization(OrganizationVisibility.PRIVATE, userId);
        var secret = secret(SecretVisibility.PRIVATE, UUID.randomUUID(), Set.of());

        assertSame(secret, read(secret));
        secrets.list(orgId, 0, 10, null);

        verifyNoInteractions(userManager);
        verify(secretDao, never()).hasAccessLevel(any(), any(), any());
        verify(secretDao).list(orgId, userId, SECRETS.SECRET_NAME, true, 0, 10, null);
    }

    @Test
    void adminRetainsPrivateOrganizationAccess() {
        assertPrivilegedAccess(Roles.ADMIN, ResourceAccessLevel.OWNER);
    }

    @Test
    void globalReaderRetainsPrivateOrganizationReadAccessButNotWriteAclBypass() {
        var secret = assertPrivilegedAccess(Roles.SYSTEM_READER, ResourceAccessLevel.READER);

        assertThrows(UnauthorizedException.class, () -> secrets.assertAccess(orgId, null, secret.getName(),
                ResourceAccessLevel.WRITER, false));
        verify(secretDao).hasAccessLevel(eq(secret.getId()), eq(userId), any());
    }

    @Test
    void globalWriterRetainsPrivateOrganizationWriteAccess() {
        assertPrivilegedAccess(Roles.SYSTEM_WRITER, ResourceAccessLevel.WRITER);
    }

    @Test
    void explicitNonMembersOnlyOrganizationAccessRetainsExistingSemantics() {
        assertEquals(orgId, orgManager.assertAccess(orgId, false).getId());
        assertThrows(UnauthorizedException.class, () -> orgManager.assertReadAccess(orgId));
        verify(userManager).isInOrganization(tx, orgId);
    }

    private SecretEntryV2 assertPrivilegedAccess(String role, ResourceAccessLevel level) {
        when(subject.hasRole(role)).thenReturn(true);
        var secret = secret(SecretVisibility.PRIVATE, UUID.randomUUID(), Set.of());

        assertSame(secret, secrets.assertAccess(orgId, null, secret.getName(), level, false));
        secrets.list(orgId, 0, 10, null);

        verifyNoInteractions(userManager);
        verify(secretDao, never()).hasAccessLevel(any(), any(), any());
        verify(secretDao).list(orgId, null, SECRETS.SECRET_NAME, true, 0, 10, null);
        return secret;
    }

    private SecretEntryV2 read(SecretEntryV2 secret) {
        return secrets.assertAccess(orgId, null, secret.getName(), ResourceAccessLevel.READER, false);
    }

    private void organization(OrganizationVisibility visibility, UUID ownerId) {
        when(orgDao.get(tx, orgId)).thenReturn(new OrganizationEntry(orgId, "test-org",
                EntityOwner.builder().id(ownerId).build(), visibility, null, null));
    }

    private SecretEntryV2 secret(SecretVisibility visibility, UUID ownerId, Set<ProjectEntry> projects) {
        var secret = new SecretEntryV2(UUID.randomUUID(), "test-secret", orgId, "test-org", projects,
                SecretType.DATA, SecretEncryptedByType.PASSWORD, "test", visibility,
                EntityOwner.builder().id(ownerId).build(), null, null, new byte[16], HashAlgorithm.SHA256);
        when(secretDao.get(secret.getId())).thenReturn(secret);
        when(secretDao.getByName(orgId, secret.getName())).thenReturn(secret);
        return secret;
    }
}
