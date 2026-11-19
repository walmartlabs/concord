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
import com.walmartlabs.concord.server.audit.AuditLog;
import com.walmartlabs.concord.server.sdk.ConcordApplicationException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.sdk.security.AuthenticationException;
import com.walmartlabs.concord.server.security.Permission;
import com.walmartlabs.concord.server.security.UnauthorizedException;
import com.walmartlabs.concord.server.security.UserSecurityContext;
import com.walmartlabs.concord.server.security.apikey.ApiKey;
import com.walmartlabs.concord.server.security.apikey.ApiKeyRealm;
import org.apache.shiro.mgt.SecurityManager;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    private static ExternalIdentityRef identity() {
        return new ExternalIdentityRef("provider", "authority", UUID.randomUUID().toString());
    }
}
