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

import com.walmartlabs.concord.db.DataSourceUtils;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.cfg.ExternalIdentityConfiguration;
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentitySnapshot;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityProvider;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.UserPrincipal;
import com.walmartlabs.concord.server.security.ldap.LdapGroupDao;
import com.walmartlabs.concord.server.process.form.FormAccessManager;
import com.walmartlabs.concord.server.process.state.ProcessStateManager;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.util.ThreadContext;
import org.jooq.Configuration;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.walmartlabs.concord.server.jooq.Tables.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ExternalIdentityDaoTest {

    private static PostgreSQLContainer<?> db;
    private static Configuration configuration;

    private ExternalIdentityDao identities;
    private UserDao users;

    @BeforeAll
    public static void startDatabase() {
        db = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("db.image"))
                .asCompatibleSubstituteFor("postgres"));
        db.start();
        DataSourceUtils.migrateDb(new DatabaseConfiguration() {
            @Override
            public String url() {
                return db.getJdbcUrl();
            }

            @Override
            public String username() {
                return db.getUsername();
            }

            @Override
            public String password() {
                return db.getPassword();
            }

            @Override
            public int maxPoolSize() {
                return 10;
            }

            @Override
            public Duration maxLifetime() {
                return Duration.ofMinutes(1);
            }

            @Override
            public Map<String, Object> changeLogParameters() {
                var fakeSecret = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
                return Map.of("createExtensionAvailable", true,
                        "defaultAdminToken", fakeSecret,
                        "skipAdminTokenGeneration", true,
                        "defaultAgentToken", fakeSecret,
                        "skipAgentTokenGeneration", true,
                        "secretStoreSalt", fakeSecret,
                        "serverPassword", fakeSecret);
            }
        }, new MainDBChangeLogProvider());

        var ds = new PGSimpleDataSource();
        ds.setUrl(db.getJdbcUrl());
        ds.setUser(db.getUsername());
        ds.setPassword(db.getPassword());
        configuration = DataSourceUtils.createJooqConfiguration(ds);
    }

    @AfterAll
    public static void stopDatabase() {
        if (db != null) {
            db.stop();
        }
    }

    @BeforeEach
    public void setUp() {
        identities = new ExternalIdentityDao(configuration, new UuidGenerator());
        users = new UserDao(configuration, new UuidGenerator());
    }

    @Test
    public void testProvisioningAndRetryPreserveUserState() {
        var ref = identity();
        assertTrue(identities.getUserId(ref).isEmpty());
        var id = identities.getOrCreate(ref, "Example App", "app@example.org");
        var user = users.get(id);

        assertEquals(Optional.of(id), identities.getUserId(ref));
        assertEquals(UserType.EXTERNAL, user.getType());
        assertTrue(user.isDisabled());
        assertNotNull(user.getDisabledDate());
        assertTrue(user.getRoles().isEmpty());
        assertTrue(user.getOrgs().isEmpty());
        assertEquals(0, DSL.using(configuration).fetchCount(USER_TEAMS, USER_TEAMS.USER_ID.eq(id)));

        users.disable(id, true);
        assertEquals(id, identities.getOrCreate(ref, "Changed name", "changed@example.org"));
        user = users.get(id);
        assertEquals("Example App", user.getDisplayName());
        assertEquals("app@example.org", user.getEmail());
        assertTrue(user.isDisabled());
        assertTrue(user.isPermanentlyDisabled());
    }

    @Test
    public void testNamespaceAndCaseSensitivity() {
        var ref = identity();
        var ids = new HashSet<UUID>();
        ids.add(identities.getOrCreate(ref, null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef("other", ref.authority(), ref.subjectId()), null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef(ref.providerId(), "other", ref.subjectId()), null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef(ref.providerId(), ref.authority(), "other"), null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef(ref.providerId().toUpperCase(), ref.authority(), ref.subjectId()), null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef(ref.providerId(), ref.authority().toUpperCase(), ref.subjectId()), null, null));
        ids.add(identities.getOrCreate(new ExternalIdentityRef(ref.providerId(), ref.authority(), ref.subjectId().toUpperCase()), null, null));
        assertEquals(7, ids.size());
    }

    @Test
    public void testConcurrentProvisioningLeavesNoOrphans() throws Exception {
        var ref = identity();
        var before = DSL.using(configuration).fetchCount(USERS);
        var ready = new CountDownLatch(6);
        var generator = mock(UuidGenerator.class);
        when(generator.generate()).thenAnswer(invocation -> {
            ready.countDown();
            if (!ready.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent provisioners did not reach the insert");
            }
            return UUID.randomUUID();
        });
        var dao = new ExternalIdentityDao(configuration, generator);
        var ids = new HashSet<UUID>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<Future<UUID>>();
            for (var i = 0; i < 6; i++) {
                futures.add(executor.submit(() -> dao.getOrCreate(ref, "Concurrent App", null)));
            }
            for (var future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }
        }
        assertEquals(1, ids.size());
        assertEquals(before + 1, DSL.using(configuration).fetchCount(USERS));
        assertEquals(ids.iterator().next(), identities.getUserId(ref).orElseThrow());
    }

    @Test
    public void testAuthoritativeAliasCannotBeReassignedByEitherWriter() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var otherRef = identity();
        var otherId = identities.getOrCreate(otherRef, null, null);
        var alias = "github-" + UUID.randomUUID();

        identities.bindAlias(ref, alias);
        identities.bindAlias(ref, alias);
        users.createExternalUserMapping(id, alias);
        assertThrows(ConcordApplicationException.class, () -> identities.bindAlias(otherRef, alias));
        assertThrows(ConcordApplicationException.class, () -> users.createExternalUserMapping(otherId, alias));

        assertEquals(id, users.getUserFromExternalMapping(alias).getId());
        assertTrue(DSL.using(configuration).select(EXTERNAL_APP_USERS.IS_AUTHORITATIVE)
                .from(EXTERNAL_APP_USERS).where(EXTERNAL_APP_USERS.EXTERNAL_USER_ID.eq(alias))
                .fetchSingle(EXTERNAL_APP_USERS.IS_AUTHORITATIVE));
    }

    @Test
    public void testLegacyMappingCompatibilityAndPromotion() {
        var first = users.insertOrUpdate("human-" + UUID.randomUUID(), "example.org",
                "Human User", null, UserType.LDAP, null);
        var secondRef = identity();
        var second = identities.getOrCreate(secondRef, null, null);
        var alias = "legacy-" + UUID.randomUUID();

        users.createExternalUserMapping(first, alias);
        assertFalse(DSL.using(configuration).select(EXTERNAL_APP_USERS.IS_AUTHORITATIVE)
                .from(EXTERNAL_APP_USERS).where(EXTERNAL_APP_USERS.EXTERNAL_USER_ID.eq(alias))
                .fetchSingle(EXTERNAL_APP_USERS.IS_AUTHORITATIVE));
        assertThrows(ConcordApplicationException.class, () -> identities.bindAlias(secondRef, alias));
        users.createExternalUserMapping(second, alias);
        assertEquals(second, users.getUserFromExternalMapping(alias).getId());
        identities.bindAlias(secondRef, alias);
        assertThrows(ConcordApplicationException.class, () -> users.createExternalUserMapping(first, alias));
    }

    @Test
    public void testExactPersistenceLimits() {
        var ref = new ExternalIdentityRef("p".repeat(128), "a".repeat(512), "s".repeat(512));
        var id = identities.getOrCreate(ref, null, null);
        var alias = "x".repeat(512);
        identities.bindAlias(ref, alias);
        assertEquals(Optional.of(id), identities.getUserId(ref));
        assertEquals(id, users.getUserFromExternalMapping(alias).getId());
    }

    @Test
    public void testConcurrentAliasClaimsCannotReassign() throws Exception {
        var firstRef = identity();
        var first = identities.getOrCreate(firstRef, null, null);
        var secondRef = identity();
        var second = identities.getOrCreate(secondRef, null, null);
        var alias = "race-" + UUID.randomUUID();
        var ready = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = new ArrayList<Future<Boolean>>();
            for (var ref : new ExternalIdentityRef[]{firstRef, secondRef}) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!ready.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent alias claimers did not start");
                    }
                    try {
                        identities.bindAlias(ref, alias);
                        return true;
                    } catch (ConcordApplicationException e) {
                        assertEquals("External alias is already bound to another user", e.getMessage());
                        return false;
                    }
                }));
            }
            var wins = 0;
            for (var result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    wins++;
                }
            }
            assertEquals(1, wins);
        }
        assertTrue(Set.of(first, second).contains(users.getUserFromExternalMapping(alias).getId()));
    }

    @Test
    public void testBindingUniquenessAndCascadingDelete() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var alias = "cascade-" + UUID.randomUUID();
        identities.bindAlias(ref, alias);
        assertThrows(DataAccessException.class, () -> DSL.using(configuration).insertInto(EXTERNAL_IDENTITIES)
                .set(EXTERNAL_IDENTITIES.PROVIDER_ID, "another-provider")
                .set(EXTERNAL_IDENTITIES.AUTHORITY, "authority")
                .set(EXTERNAL_IDENTITIES.SUBJECT_ID, "subject")
                .set(EXTERNAL_IDENTITIES.USER_ID, id)
                .execute());

        users.delete(id);
        assertTrue(identities.getUserId(ref).isEmpty());
        assertNull(users.getUserFromExternalMapping(alias));
    }

    @Test
    public void testInvalidInputAndFailedInsertLeaveNoPartialState() {
        var ref = identity();
        var before = DSL.using(configuration).fetchCount(USERS);
        assertThrows(IllegalArgumentException.class, () -> identities.getOrCreate(
                new ExternalIdentityRef("p".repeat(129), "authority", "subject"), null, null));
        assertThrows(IllegalArgumentException.class, () -> identities.getOrCreate(
                new ExternalIdentityRef("provider", "a".repeat(513), "subject"), null, null));
        assertThrows(IllegalArgumentException.class, () -> identities.getOrCreate(
                new ExternalIdentityRef("provider", "authority", "s".repeat(513)), null, null));
        assertThrows(DataAccessException.class, () -> identities.getOrCreate(ref,
                "n".repeat(UserEntry.MAX_DISPLAY_NAME_LENGTH + 1), null));
        assertTrue(identities.getUserId(ref).isEmpty());
        assertThrows(ConcordApplicationException.class, () -> identities.bindAlias(ref, "not-provisioned"));
        assertThrows(IllegalArgumentException.class, () -> identities.bindAlias(ref, " "));
        assertThrows(IllegalArgumentException.class, () -> identities.bindAlias(ref, "x".repeat(513)));
        assertEquals(before, DSL.using(configuration).fetchCount(USERS));
    }

    @Test
    public void testExecutionGatesEvenIfUserIsManuallyEnabled() {
        var id = identities.getOrCreate(identity(), null, null);
        users.enable(id);
        var manager = mock(UserManager.class);
        when(manager.get(id)).thenReturn(Optional.of(users.get(id)));

        var context = new UserSecurityContext(mock(SecurityManager.class), manager);
        assertFalse(context.isPermitted(id, Permission.CREATE_ORG));
        assertThrows(UnauthorizedException.class, () -> context.runAs(id, () -> {
            fail("External identity must not execute");
            return null;
        }));

        var realm = new ApiKeyRealm(manager, mock(AuditLog.class));
        assertThrows(AuthenticationException.class, () -> realm.getAuthenticationInfo(
                new ApiKey(UUID.randomUUID(), id, "test-key", false)));
    }

    @Test
    public void testAtomicMembershipReplacementAndLifecycle() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        assertNull(identities.getSyncState(ref).orElseThrow().lastSuccessfulSync());
        assertTrue(identities.applySnapshot(snapshot(ref, Set.of("first", "second")), identities.beginRefresh(ref)));
        assertEquals(Set.of("first", "second"), groups(id));
        var state = identities.getSyncState(ref).orElseThrow();
        assertEquals(ExternalIdentitySnapshot.Status.ACTIVE, state.status());
        assertNotNull(state.lastSuccessfulSync());
        assertTrue(users.get(id).isDisabled());

        assertTrue(identities.applySnapshot(snapshot(ref, Set.of("replacement")), identities.beginRefresh(ref)));
        assertEquals(Set.of("replacement"), groups(id));
        assertTrue(identities.applySnapshot(snapshot(ref, Set.of()), identities.beginRefresh(ref)));
        assertTrue(groups(id).isEmpty());
        assertEquals(ExternalIdentitySnapshot.Status.ACTIVE, identities.getSyncState(ref).orElseThrow().status());

        users.disable(id, true);
        for (var status : new ExternalIdentitySnapshot.Status[]{
                ExternalIdentitySnapshot.Status.INACTIVE, ExternalIdentitySnapshot.Status.NOT_FOUND}) {
            assertTrue(identities.applySnapshot(snapshot(ref, Set.of("removed")), identities.beginRefresh(ref)));
            assertTrue(identities.applySnapshot(new ExternalIdentitySnapshot(ref, status, null, null, Set.of()),
                    identities.beginRefresh(ref)));
            assertTrue(groups(id).isEmpty());
            assertEquals(status, identities.getSyncState(ref).orElseThrow().status());
            assertTrue(users.get(id).isPermanentlyDisabled());
            assertTrue(users.get(id).isDisabled());
        }
        users.enable(id);
        assertTrue(identities.applySnapshot(new ExternalIdentitySnapshot(ref,
                ExternalIdentitySnapshot.Status.INACTIVE, null, null, Set.of()), identities.beginRefresh(ref)));
        assertFalse(users.get(id).isDisabled());
        assertEquals(ExternalIdentitySnapshot.Status.INACTIVE, identities.getSyncState(ref).orElseThrow().status());
    }

    @Test
    public void testSupersededReplayAndRecreatedBindingCannotApply() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var older = identities.beginRefresh(ref);
        var newer = identities.beginRefresh(ref);
        assertFalse(identities.applySnapshot(snapshot(ref, Set.of("older")), older));
        assertTrue(identities.applySnapshot(snapshot(ref, Set.of("newer")), newer));
        assertFalse(identities.applySnapshot(snapshot(ref, Set.of("replay")), newer));
        assertEquals(Set.of("newer"), groups(id));
        var priorState = identities.getSyncState(ref).orElseThrow();
        var pending = identities.beginRefresh(ref);
        identities.beginRefresh(ref);
        assertFalse(identities.applySnapshot(snapshot(ref, Set.of("late")), pending));
        assertEquals(priorState, identities.getSyncState(ref).orElseThrow());

        users.delete(id);
        var replacement = identities.getOrCreate(ref, null, null);
        identities.beginRefresh(ref);
        assertFalse(identities.applySnapshot(snapshot(ref, Set.of("deleted-user")), older));
        assertTrue(groups(replacement).isEmpty());
        assertNull(identities.getSyncState(ref).orElseThrow().lastSuccessfulSync());
    }

    @Test
    public void testFailedSnapshotRollsBackMembershipAndMetadata() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        identities.applySnapshot(snapshot(ref, Set.of("original")), identities.beginRefresh(ref));
        var state = identities.getSyncState(ref).orElseThrow();
        var invalid = new ExternalIdentitySnapshot(ref, ExternalIdentitySnapshot.Status.ACTIVE,
                "n".repeat(UserEntry.MAX_DISPLAY_NAME_LENGTH + 1), null, Set.of("replacement"));
        assertThrows(DataAccessException.class, () -> identities.applySnapshot(invalid, identities.beginRefresh(ref)));
        assertEquals(Set.of("original"), groups(id));
        assertEquals(state, identities.getSyncState(ref).orElseThrow());
    }

    @Test
    public void testProviderFailuresPreserveLastSuccessfulSnapshot() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenReturn(snapshot(ref, Set.of("original")));
        var synchronizer = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class, RETURNS_DEEP_STUBS), Set.of(provider));
        synchronizer.refresh(ref);
        var state = identities.getSyncState(ref).orElseThrow();
        when(provider.fetch(ref)).thenThrow(new ExternalIdentityException("Authority unavailable"));
        assertThrows(ExternalIdentityException.class, () -> synchronizer.refresh(ref));
        doReturn(snapshot(identity(), Set.of("wrong-identity"))).when(provider).fetch(ref);
        assertThrows(ExternalIdentityException.class, () -> synchronizer.refresh(ref));
        doReturn(null).when(provider).fetch(ref);
        assertThrows(ExternalIdentityException.class, () -> synchronizer.refresh(ref));
        assertEquals(Set.of("original"), groups(id));
        assertEquals(state, identities.getSyncState(ref).orElseThrow());

        var duplicate = mock(ExternalIdentityProvider.class);
        when(duplicate.getId()).thenReturn(ref.providerId());
        assertThrows(ConcordApplicationException.class, () -> new ExternalIdentitySynchronizer(
                identities, mock(AuditLog.class), Set.of(provider, duplicate)));
        when(duplicate.getId()).thenReturn(" ");
        assertThrows(ConcordApplicationException.class, () -> new ExternalIdentitySynchronizer(
                identities, mock(AuditLog.class), Set.of(duplicate)));
        var empty = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class), Set.of());
        assertThrows(ExternalIdentityException.class, () -> empty.refresh(ref));
        assertEquals(state, identities.getSyncState(ref).orElseThrow());
    }

    @Test
    public void testLifecycleRefreshOnDemandAndImmediateDisableChecks() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenReturn(snapshot(ref, Set.of("current")));
        var synchronizer = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class, RETURNS_DEEP_STUBS), Set.of(provider));
        var lifecycle = lifecycle(synchronizer);

        var initial = identities.getLifecycleState(ref).orElseThrow();
        assertEquals(id, initial.userId());
        assertEquals(UserType.EXTERNAL, initial.userType());
        assertTrue(initial.disabled());
        assertFalse(initial.permanentlyDisabled());
        assertNull(initial.status());
        assertNull(initial.lastSuccessfulSync());
        assertNotNull(initial.checkedAt());
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        verify(provider, never()).fetch(any());

        users.enable(id);
        assertEquals(id, lifecycle.requireActive(ref));
        assertEquals(Set.of("current"), groups(id));
        var state = identities.getLifecycleState(ref).orElseThrow();
        assertFalse(state.disabled());
        assertEquals(ExternalIdentitySnapshot.Status.ACTIVE, state.status());
        assertFalse(state.checkedAt().isBefore(state.lastSuccessfulSync()));
        assertEquals(id, lifecycle.requireActive(ref));
        verify(provider, times(1)).fetch(ref);

        users.disable(id, false);
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        users.enable(id);
        DSL.using(configuration).update(USERS).set(USERS.IS_PERMANENTLY_DISABLED, true)
                .where(USERS.USER_ID.eq(id)).execute();
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        verify(provider, times(1)).fetch(ref);
    }

    @Test
    public void testExpiredLifecycleRefreshFailureAndSourceRevocation() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        users.enable(id);
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenReturn(snapshot(ref, Set.of("original")));
        var synchronizer = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class, RETURNS_DEEP_STUBS), Set.of(provider));
        var lifecycle = lifecycle(synchronizer);
        lifecycle.requireActive(ref);
        DSL.using(configuration).update(EXTERNAL_IDENTITIES)
                .set(EXTERNAL_IDENTITIES.LAST_SUCCESSFUL_SYNC, java.time.OffsetDateTime.now().minusMinutes(16))
                .where(EXTERNAL_IDENTITIES.USER_ID.eq(id)).execute();
        var before = identities.getSyncState(ref).orElseThrow();

        doThrow(new ExternalIdentityException("Provider unavailable")).when(provider).fetch(ref);
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        assertEquals(before, identities.getSyncState(ref).orElseThrow());
        assertEquals(Set.of("original"), groups(id));

        doReturn(new ExternalIdentitySnapshot(ref, ExternalIdentitySnapshot.Status.INACTIVE, null, null, Set.of()))
                .when(provider).fetch(ref);
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        assertTrue(groups(id).isEmpty());
        assertEquals(ExternalIdentitySnapshot.Status.INACTIVE, identities.getSyncState(ref).orElseThrow().status());
        assertFalse(users.get(id).isDisabled());
        assertThrows(ExternalIdentityException.class, () -> lifecycle.requireActive(ref));
        verify(provider, times(3)).fetch(ref);
    }

    @Test
    public void testLifecycleRejectsProviderRemovalAndBindingChanges() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        users.enable(id);
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenReturn(snapshot(ref, Set.of()));
        var synchronizer = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class, RETURNS_DEEP_STUBS), Set.of(provider));
        lifecycle(synchronizer).requireActive(ref);
        assertThrows(ExternalIdentityException.class, () -> lifecycle(
                new ExternalIdentitySynchronizer(identities, mock(AuditLog.class), Set.of())).requireActive(ref));

        DSL.using(configuration).update(USERS).set(USERS.USER_TYPE, UserType.LOCAL.name())
                .where(USERS.USER_ID.eq(id)).execute();
        assertEquals(UserType.LOCAL, identities.getLifecycleState(ref).orElseThrow().userType());
        assertThrows(ExternalIdentityException.class, () -> lifecycle(synchronizer).requireActive(ref));
        verify(provider, times(1)).fetch(ref);
        users.delete(id);
        assertTrue(identities.getLifecycleState(ref).isEmpty());
        assertThrows(ExternalIdentityException.class, () -> lifecycle(synchronizer).requireActive(ref));
    }

    @Test
    public void testDisableDuringProviderLookupIsNotOverriddenBySuccessfulRefresh() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        users.enable(id);
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenAnswer(invocation -> {
            users.disable(id, true);
            return snapshot(ref, Set.of("updated"));
        });
        var synchronizer = new ExternalIdentitySynchronizer(identities, mock(AuditLog.class, RETURNS_DEEP_STUBS), Set.of(provider));
        assertThrows(ExternalIdentityException.class, () -> lifecycle(synchronizer).requireActive(ref));
        assertEquals(Set.of("updated"), groups(id));
        assertTrue(users.get(id).isDisabled());
        assertTrue(users.get(id).isPermanentlyDisabled());
        assertEquals(ExternalIdentitySnapshot.Status.ACTIVE, identities.getSyncState(ref).orElseThrow().status());
    }

    @Test
    public void testExternalGroupsGrantTeamsButNotGlobalRoles() {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var ldap = users.insertOrUpdate("ldap-" + UUID.randomUUID(), "example.org", null, null, UserType.LDAP, null);
        var local = users.insertOrUpdate("local-" + UUID.randomUUID(), null, null, null, UserType.LOCAL, null);
        var sso = users.insertOrUpdate("sso-" + UUID.randomUUID(), "example.org", null, null, UserType.SSO, null);
        var tx = DSL.using(configuration);
        var group = "CN=" + UUID.randomUUID() + ",DC=example";
        var role = UUID.randomUUID();
        var directRole = UUID.randomUUID();
        var team = UUID.randomUUID();
        var directTeam = UUID.randomUUID();
        tx.insertInto(ROLES).set(ROLES.ROLE_ID, role).set(ROLES.ROLE_NAME, "role-" + role).execute();
        tx.insertInto(ROLES).set(ROLES.ROLE_ID, directRole).set(ROLES.ROLE_NAME, "role-" + directRole).execute();
        tx.insertInto(ROLE_LDAP_GROUPS).set(ROLE_LDAP_GROUPS.ROLE_ID, role)
                .set(ROLE_LDAP_GROUPS.LDAP_GROUP, group).execute();
        tx.insertInto(USER_ROLES).set(USER_ROLES.USER_ID, id).set(USER_ROLES.ROLE_ID, directRole).execute();
        var orgId = tx.select(ORGANIZATIONS.ORG_ID).from(ORGANIZATIONS)
                .where(ORGANIZATIONS.ORG_NAME.eq("Default")).fetchSingle(ORGANIZATIONS.ORG_ID);
        for (var teamId : Set.of(team, directTeam)) {
            tx.insertInto(TEAMS).set(TEAMS.TEAM_ID, teamId).set(TEAMS.TEAM_NAME, "team-" + teamId)
                    .set(TEAMS.ORG_ID, orgId).execute();
        }
        tx.insertInto(USER_TEAMS).set(USER_TEAMS.USER_ID, id).set(USER_TEAMS.TEAM_ID, directTeam)
                .set(USER_TEAMS.TEAM_ROLE, "MEMBER").execute();
        tx.insertInto(TEAM_LDAP_GROUPS).set(TEAM_LDAP_GROUPS.TEAM_ID, team)
                .set(TEAM_LDAP_GROUPS.LDAP_GROUP, group).set(TEAM_LDAP_GROUPS.TEAM_ROLE, "MEMBER").execute();
        identities.applySnapshot(snapshot(ref, Set.of(group)), identities.beginRefresh(ref));
        var ldapGroups = new LdapGroupDao(configuration);
        for (var userId : Set.of(ldap, local, sso)) {
            ldapGroups.update(userId, Set.of(group));
            assertEquals(Set.of(role), tx.select(V_USER_ROLES.ROLE_ID).from(V_USER_ROLES)
                    .where(V_USER_ROLES.USER_ID.eq(userId)).fetchSet(V_USER_ROLES.ROLE_ID));
        }
        assertEquals(Set.of(directRole), tx.select(V_USER_ROLES.ROLE_ID).from(V_USER_ROLES)
                .where(V_USER_ROLES.USER_ID.eq(id)).fetchSet(V_USER_ROLES.ROLE_ID));
        assertEquals(Set.of(team, directTeam), tx.select(V_USER_TEAMS.TEAM_ID).from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(id)).fetchSet(V_USER_TEAMS.TEAM_ID));
        assertThrows(ConcordApplicationException.class, () -> ldapGroups.update(id, Set.of("wrong-owner")));
        assertThrows(ConcordApplicationException.class, () -> ldapGroups.updateIfNeeded(id, Set.of("wrong-owner"),
                org.jooq.impl.DSL.currentOffsetDateTime()));
        assertEquals(Set.of(group), groups(id));
        identities.applySnapshot(snapshot(ref, Set.of()), identities.beginRefresh(ref));
        assertEquals(Set.of(directTeam), tx.select(V_USER_TEAMS.TEAM_ID).from(V_USER_TEAMS)
                .where(V_USER_TEAMS.USER_ID.eq(id)).fetchSet(V_USER_TEAMS.TEAM_ID));
        assertEquals(Set.of(directRole), tx.select(V_USER_ROLES.ROLE_ID).from(V_USER_ROLES)
                .where(V_USER_ROLES.USER_ID.eq(id)).fetchSet(V_USER_ROLES.ROLE_ID));
    }

    @Test
    public void testOverlappingProviderLookupsCannotApplyOutOfOrder() throws Exception {
        var ref = identity();
        var id = identities.getOrCreate(ref, null, null);
        var firstStarted = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var provider = mock(ExternalIdentityProvider.class);
        when(provider.getId()).thenReturn(ref.providerId());
        when(provider.fetch(ref)).thenAnswer(invocation -> {
            if (calls.getAndIncrement() == 0) {
                firstStarted.countDown();
                if (!releaseFirst.await(20, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("First lookup was not released");
                }
                return snapshot(ref, Set.of("older"));
            }
            return snapshot(ref, Set.of("newer"));
        });
        var audit = mock(AuditLog.class, RETURNS_DEEP_STUBS);
        var synchronizer = new ExternalIdentitySynchronizer(identities, audit, Set.of(provider));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> assertThrows(ExternalIdentityException.class,
                    () -> synchronizer.refresh(ref)));
            try {
                assertTrue(firstStarted.await(20, TimeUnit.SECONDS));
                synchronizer.refresh(ref);
            } finally {
                releaseFirst.countDown();
            }
            assertEquals("External identity refresh was superseded or its binding was deleted",
                    first.get(30, TimeUnit.SECONDS).getMessage());
        }
        assertEquals(Set.of("newer"), groups(id));
        verify(audit, times(1)).add(any(), any());
    }

    @Test
    public void testExternalGroupsDoNotSatisfyLdapFormAccess() {
        var id = identities.getOrCreate(identity(), null, null);
        var subject = mock(Subject.class);
        when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(
                new UserPrincipal("internal", users.get(id)), "internal"));
        var previous = ThreadContext.getResources();
        try {
            ThreadContext.bind(mock(SecurityManager.class));
            ThreadContext.bind(subject);
            var usersManager = mock(UserManager.class);
            var manager = new FormAccessManager(mock(ProcessStateManager.class), usersManager);
            var runAs = new HashMap<String, Serializable>();
            runAs.put("ldap", new HashMap<>(Map.of("group", "CN=group,DC=example")));
            assertThrows(UnauthorizedException.class, () -> manager.assertFormAccess(
                    "approval", runAs));
            verifyNoInteractions(usersManager);
            for (var type : new UserType[]{UserType.LDAP, UserType.LOCAL, UserType.SSO}) {
                var user = mock(UserEntry.class);
                when(user.getType()).thenReturn(type);
                when(subject.getPrincipals()).thenReturn(new SimplePrincipalCollection(
                        new UserPrincipal("internal", user), "internal"));
                when(usersManager.getCurrentUserInfo()).thenReturn(UserInfoProvider.UserInfo.builder()
                        .groups(Set.of("CN=group,DC=example")).build());
                manager.assertFormAccess("approval", runAs);
            }
        } finally {
            ThreadContext.remove();
            ThreadContext.setResources(previous);
        }
    }

    private Set<String> groups(UUID id) {
        return DSL.using(configuration).select(USER_LDAP_GROUPS.LDAP_GROUP).from(USER_LDAP_GROUPS)
                .where(USER_LDAP_GROUPS.USER_ID.eq(id)).fetchSet(USER_LDAP_GROUPS.LDAP_GROUP);
    }

    private ExternalIdentityLifecycleManager lifecycle(ExternalIdentitySynchronizer synchronizer) {
        var cfg = mock(ExternalIdentityConfiguration.class);
        when(cfg.getMaxSnapshotAge()).thenReturn(Duration.ofMinutes(15));
        return new ExternalIdentityLifecycleManager(identities, synchronizer, cfg);
    }

    private static ExternalIdentitySnapshot snapshot(ExternalIdentityRef ref, Set<String> groups) {
        return new ExternalIdentitySnapshot(ref, ExternalIdentitySnapshot.Status.ACTIVE, "App", null, groups);
    }

    private static ExternalIdentityRef identity() {
        return new ExternalIdentityRef("provider", "authority", UUID.randomUUID().toString());
    }
}
