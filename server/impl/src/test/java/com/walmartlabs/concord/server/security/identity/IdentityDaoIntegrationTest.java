package com.walmartlabs.concord.server.security.identity;

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

import com.codahale.metrics.MetricRegistry;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.UuidGenerator;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import javax.ws.rs.ClientErrorException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityDaoIntegrationTest {
    private static PostgreSQLContainer<?> database;
    private static DataSource dataSource;
    private static DSLContext sql;
    private static IdentityDao dao;
    private static final ProvisioningPolicy POLICY = new ProvisioningPolicy("oauth", true, "local", Set.of(), List.of(), null);
    private static final UserProfile PROFILE = new UserProfile("same", null, "Name", null, Map.of());

    @BeforeAll
    static void start() throws Exception {
        database = new PostgreSQLContainer<>(DockerImageName.parse(System.getProperty("db.image", "library/postgres:14.21-alpine")).asCompatibleSubstituteFor("postgres"));
        database.start();
        DatabaseConfiguration config = new DatabaseConfiguration() {
            public String url() { return database.getJdbcUrl(); }
            public String username() { return database.getUsername(); }
            public String password() { return database.getPassword(); }
            public int maxPoolSize() { return 8; }
            public Duration maxLifetime() { return Duration.ofMinutes(30); }
            public Map<String, Object> changeLogParameters() {
                return Map.of("createExtensionAvailable", "true", "defaultAdminToken", encoded("foobar"),
                        "skipAdminTokenGeneration", "false", "defaultAgentToken", encoded("barbaz"),
                        "skipAgentTokenGeneration", "false", "secretStoreSalt", encoded("foo"), "serverPassword", encoded("bar"));
            }
        };
        DatabaseModule module = new DatabaseModule(true);
        dataSource = module.appDataSource(config, new MetricRegistry(), Set.of(new MainDBChangeLogProvider()));
        var jooq = module.appJooqConfiguration(dataSource);
        sql = DSL.using(jooq);
        UserDirectory local = mock(UserDirectory.class);
        when(local.id()).thenReturn("local");
        ProviderRegistry registry = new ProviderRegistry(Set.of(local), Set.of(), Set.of(POLICY), Set.of());
        dao = new IdentityDao(jooq, registry, new UserDao(jooq, new UuidGenerator()));
    }

    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(UTF_8)); }

    @AfterAll
    static void stop() throws Exception {
        if (dataSource != null) dataSource.getClass().getMethod("close").invoke(dataSource);
        if (database != null) database.stop();
    }

    private static UUID legacy(String username, boolean disabled) {
        UUID id = UUID.randomUUID();
        sql.transaction(configuration -> {
            var tx = DSL.using(configuration);
            tx.execute("insert into users(user_id,username,user_type,is_disabled) values (?,?,?,?)", id, username, "LOCAL", disabled);
            tx.execute("insert into legacy_user_enrollment(user_id,username,domain,user_type) values (?,?,null,'LOCAL')", id, username);
        });
        return id;
    }

    private static VerifiedIdentity verified(ExternalIdentityKey key) {
        return new VerifiedIdentity(key, PROFILE, Set.of(), Map.of(), null, null);
    }

    private static IdentityDao.Resolution resolve(ExternalIdentityKey key, String username) {
        return dao.resolve(verified(key), POLICY, new LegacyAccountSelector(username, null, "LOCAL"), List.of(), List.of(), null);
    }

    @Test
    void adoptionRetirementAndExplicitRecovery() {
        String name = UUID.randomUUID().toString();
        UUID id = legacy(name, false);
        ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        assertEquals(id, resolve(key, name).userId());
        var link = dao.list(id).get(0);
        dao.unlink(id, link.identityId());
        assertThrows(AuthenticationException.class, () -> resolve(key, name));
        ExternalIdentityKey different = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        assertThrows(AuthenticationException.class, () -> resolve(different, name));
        assertEquals(link.identityId(), dao.link(id, key).identityId());
        assertEquals(id, resolve(key, name).userId());
        assertEquals(1, sql.fetchCount(DSL.table("legacy_identity_adoptions"), DSL.field("user_id").eq(id)));
    }

    @Test
    void disabledCandidatesAndDuplicateSnapshotsNeverAdopt() {
        String disabled = UUID.randomUUID().toString();
        legacy(disabled, true);
        ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        assertThrows(AuthenticationException.class, () -> resolve(key, disabled));
        assertTrue(dao.find(key).isEmpty());
        String duplicate = UUID.randomUUID().toString();
        legacy(duplicate, false);
        legacy(duplicate, false);
        assertThrows(ClientErrorException.class, () -> resolve(key, duplicate));
        assertTrue(dao.find(key).isEmpty());
    }

    @Test
    void concurrentLinksHaveOneOwnerAndSameNameAccountsStaySeparate() throws Exception {
        UUID first = legacy(UUID.randomUUID().toString(), false);
        UUID second = legacy(UUID.randomUUID().toString(), false);
        ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> linkFirst = () -> linkAfter(start, first, key);
            Callable<Boolean> linkSecond = () -> linkAfter(start, second, key);
            Future<Boolean> a = executor.submit(linkFirst);
            Future<Boolean> b = executor.submit(linkSecond);
            start.countDown();
            assertNotEquals(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            assertTrue(Set.of(first, second).contains(dao.find(key).orElseThrow().userId()));
        } finally {
            executor.shutdownNow();
        }
        ExternalIdentityKey a = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        ExternalIdentityKey b = new ExternalIdentityKey("oauth", "other", UUID.randomUUID().toString());
        UUID one = dao.resolve(verified(a), POLICY, null, List.of(), List.of(), null).userId();
        UUID two = dao.resolve(verified(b), POLICY, null, List.of(), List.of(), null).userId();
        assertNotEquals(one, two);
    }

    @Test
    void concurrentAdoptionOfSameKeyConverges() throws Exception {
        String name = UUID.randomUUID().toString();
        UUID user = legacy(name, false);
        ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<UUID> login = () -> { start.await(); return resolve(key, name).userId(); };
            Future<UUID> first = executor.submit(login);
            Future<UUID> second = executor.submit(login);
            start.countDown();
            assertEquals(user, first.get(30, TimeUnit.SECONDS));
            assertEquals(user, second.get(30, TimeUnit.SECONDS));
            assertEquals(1, dao.list(user).size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void profileRequiresOwnedActiveIdentityAndRequiredBindingPreventsRetirement() {
        UUID user = legacy(UUID.randomUUID().toString(), false);
        UUID other = legacy(UUID.randomUUID().toString(), false);
        ExternalIdentityKey key = new ExternalIdentityKey("directory", "", UUID.randomUUID().toString());
        UserDirectory external = mock(UserDirectory.class);
        when(external.id()).thenReturn("directory");
        when(external.acceptsIdentity(key)).thenReturn(true);
        ProviderRegistry registry = new ProviderRegistry(Set.of(external), Set.of(), Set.of(), Set.of());
        IdentityDao scoped = new IdentityDao(sql.configuration(), registry, new UserDao(sql.configuration(), new UuidGenerator()));
        UUID identityId = scoped.link(user, key).identityId();
        assertThrows(javax.ws.rs.BadRequestException.class, () -> scoped.setProfile(other, "directory", identityId));
        scoped.setProfile(user, "directory", identityId);
        assertEquals(key, scoped.profile(user).orElseThrow().identity());
        sql.transaction(configuration -> {
            var tx = DSL.using(configuration);
            tx.execute("insert into authorization_sources(source_id,is_active) values ('profile-test',true) on conflict do nothing");
            tx.execute("insert into user_authorization_bindings(binding_id,user_id,source_id,identity_id,is_required,origin,refresh_version) values (?,?, 'profile-test',?,true,'ADMIN',0)", UUID.randomUUID(), user, identityId);
        });
        assertThrows(ClientErrorException.class, () -> scoped.unlink(user, identityId));
        sql.transaction(configuration -> DSL.using(configuration).execute("update user_authorization_bindings set is_required=false where user_id=?", user));
        scoped.unlink(user, identityId);
        assertNull(scoped.profile(user).orElseThrow().identity());
        assertEquals(0, sql.fetchCount(DSL.table("user_authorization_bindings"), DSL.field("user_id").eq(user)));
    }

    @Test
    void disabledAccountCannotReceiveVerifiedObservations() {
        UUID user = legacy(UUID.randomUUID().toString(), true);
        ExternalIdentityKey key = new ExternalIdentityKey("oauth", "issuer", UUID.randomUUID().toString());
        UUID identity = dao.link(user, key).identityId();
        sql.transaction(configuration -> {
            var tx = DSL.using(configuration);
            tx.execute("insert into authorization_sources(source_id,is_active) values ('observation-test',true) on conflict do nothing");
            tx.execute("insert into user_authorization_bindings(binding_id,user_id,source_id,identity_id,is_required,origin,refresh_version) values (?,?, 'observation-test',?,false,'ADMIN',0)", UUID.randomUUID(), user, identity);
        });
        java.time.Instant now = java.time.Instant.now();
        VerifiedIdentity observation = new VerifiedIdentity(key, PROFILE, Set.of(), Map.of("groups", List.of("group")), now, now.plusSeconds(60));
        assertThrows(AuthenticationException.class, () -> dao.observe(user, observation));
        assertEquals(0, sql.fetchCount(DSL.table("user_authorization_state"), DSL.field("user_id").eq(user)));
        sql.transaction(configuration -> DSL.using(configuration).execute("update users set is_disabled=false where user_id=?", user));
        dao.observe(user, observation);
        assertEquals(1, sql.fetchCount(DSL.table("user_authorization_state"), DSL.field("user_id").eq(user)));
    }

    private static boolean linkAfter(CountDownLatch start, UUID id, ExternalIdentityKey key) throws Exception {
        start.await();
        try { dao.link(id, key); return true; }
        catch (ClientErrorException e) { assertEquals(409, e.getResponse().getStatus()); return false; }
    }
}
