package com.walmartlabs.concord.server.security.authorization;

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
import com.typesafe.config.ConfigFactory;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceIntegrationTest {
    private static PostgreSQLContainer<?> database;
    private static DataSource dataSource;
    private static DSLContext sql;
    private static AuthorizationService service;
    private static final AtomicReference<Function<AuthorizationRequest, AuthorizationData>> resolve = new AtomicReference<>();
    private static final AuthorizationProvider provider = new AuthorizationProvider() {
        public String id() { return "test-source"; }
        public boolean isActive() { return true; }
        public boolean acceptsIdentity(ExternalIdentityKey identity) { return identity == null; }
        public Duration defaultMaxAge() { return Duration.ofHours(1); }
        public AuthorizationData resolve(AuthorizationRequest request) { return resolve.get().apply(request); }
    };

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
            public Map<String,Object> changeLogParameters() {
                return Map.of("createExtensionAvailable", "true", "defaultAdminToken", encoded("foobar"),
                        "skipAdminTokenGeneration", "false", "defaultAgentToken", encoded("barbaz"),
                        "skipAgentTokenGeneration", "false", "secretStoreSalt", encoded("foo"), "serverPassword", encoded("bar"));
            }
        };
        DatabaseModule module = new DatabaseModule(true);
        dataSource = module.appDataSource(config,new MetricRegistry(),Set.of(new MainDBChangeLogProvider()));
        var jooq = module.appJooqConfiguration(dataSource);
        sql = DSL.using(jooq);
        service = new AuthorizationService(jooq,new ProviderRegistry(Set.of(),Set.of(provider),Set.of(),Set.of()),ConfigFactory.empty());
        service.reconcile();
    }
    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(UTF_8)); }
    @AfterAll
    static void stop() throws Exception {
        if (dataSource != null) dataSource.getClass().getMethod("close").invoke(dataSource);
        if (database != null) database.stop();
    }
    private UUID account() {
        UUID id = UUID.randomUUID();
        sql.transaction(c -> DSL.using(c).execute("insert into users(user_id,username,user_type,is_disabled) values (?,?,?,false)",id,id.toString(),"LOCAL"));
        service.replaceBindings(id,List.of(new AuthorizationService.BindingSpec("test-source",null,true)));
        return id;
    }
    private static AuthorizationData groups(String... groups) { return new AuthorizationData(Set.of(groups),Set.of(),Set.of(),Instant.now().plusSeconds(3600)); }
    private static String status(UUID user) { return sql.fetchOne("select status from user_authorization_state where user_id=? and source_id='test-source'",user).get(0,String.class); }

    @Test
    void negativeAndUnavailableInvalidateUnexpiredGrants() {
        UUID user = account();
        resolve.set(r -> groups("old"));
        service.refresh(user,"test-source",true);
        assertEquals(Set.of(new GroupRef("test-source","old")),service.getGroups(user));
        resolve.set(r -> { throw new AuthorizationUnavailableException("offline"); });
        assertThrows(AuthorizationUnavailableException.class,() -> service.refresh(user,"test-source",true));
        assertEquals("UNAVAILABLE",status(user));
        assertEquals(1,sql.fetchCount(DSL.table("user_source_groups"),DSL.field("user_id").eq(user)));
        resolve.set(r -> { throw new AuthorizationSubjectNotFoundException("missing"); });
        assertThrows(AuthorizationSubjectNotFoundException.class,() -> service.refresh(user,"test-source",true));
        assertEquals("NOT_FOUND",status(user));
        assertEquals(0,sql.fetchCount(DSL.table("user_source_groups"),DSL.field("user_id").eq(user)));
        resolve.set(r -> groups());
        service.refresh(user,"test-source",true);
        assertEquals("VALID",status(user));
        assertTrue(service.getGroups(user).isEmpty());
    }

    @Test
    void invalidGrantsAreUnavailable() {
        UUID user = account();
        resolve.set(r -> new AuthorizationData(Set.of(),Set.of("missing-role-" + UUID.randomUUID()),Set.of(),null));
        assertThrows(AuthorizationUnavailableException.class,() -> service.refresh(user,"test-source",true));
        assertEquals("UNAVAILABLE",status(user));
    }

    @Test
    void directRolePermissionsSurviveSourceFailures() {
        UUID user = account();
        UUID role = UUID.randomUUID();
        UUID permission = UUID.randomUUID();
        String roleName = "role-" + role;
        String permissionName = "permission-" + permission;
        sql.transaction(c -> {
            var tx = DSL.using(c);
            tx.execute("insert into roles(role_id,role_name) values (?,?)",role,roleName);
            tx.execute("insert into permissions(permission_id,permission_name,description) values (?,?,?)",permission,permissionName,"test");
            tx.execute("insert into role_permissions(role_id,permission_id) values (?,?)",role,permission);
            tx.execute("insert into user_roles(user_id,role_id) values (?,?)",user,role);
        });
        service.replaceBindings(user,List.of(new AuthorizationService.BindingSpec("test-source",null,false)));
        resolve.set(r -> { throw new AuthorizationUnavailableException("offline"); });
        var effective = service.prepare(user);
        assertTrue(effective.roles().contains(roleName));
        assertTrue(effective.permissions().contains(permissionName));
    }

    @Test
    void qualifiedSourceConfigurationControlsDefaultBindingsAndLease() {
        UUID user = account();
        sql.transaction(c -> DSL.using(c).execute("update user_authorization_bindings set origin='DEFAULT',is_required=false where user_id=?",user));
        var configured = new AuthorizationService(sql.configuration(),
                new ProviderRegistry(Set.of(),Set.of(provider),Set.of(),Set.of()),
                ConfigFactory.parseString("com.walmartlabs.concord.server.security.authorization.sources.test-source { required=true, maxAge=1 minute }"));
        configured.reconcile();
        assertTrue(configured.listBindings(user).get(0).required());
        resolve.set(r -> groups("configured"));
        Instant before = Instant.now();
        configured.refresh(user,"test-source",true);
        assertFalse(configured.listBindings(user).get(0).validUntil().isAfter(before.plusSeconds(61)));
    }

    @Test
    void newerRefreshAndRemovedBindingDefeatLateResults() throws Exception {
        UUID user = account();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            resolve.set(r -> { entered.countDown(); try { if (!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); } catch (InterruptedException e) { throw new IllegalStateException(e); } return groups("late"); });
            Future<?> old = executor.submit(() -> service.refresh(user,"test-source",true));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            resolve.set(r -> groups("new"));
            service.refresh(user,"test-source",true);
            release.countDown();
            old.get(10,TimeUnit.SECONDS);
            assertEquals(Set.of(new GroupRef("test-source","new")),service.getGroups(user));

            CountDownLatch entered2 = new CountDownLatch(1);
            CountDownLatch release2 = new CountDownLatch(1);
            resolve.set(r -> { entered2.countDown(); try { if (!release2.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); } catch (InterruptedException e) { throw new IllegalStateException(e); } return groups("resurrected"); });
            Future<?> removed = executor.submit(() -> service.refresh(user,"test-source",true));
            assertTrue(entered2.await(10,TimeUnit.SECONDS));
            service.replaceBindings(user,List.of());
            service.replaceBindings(user,List.of(new AuthorizationService.BindingSpec("test-source",null,true)));
            release2.countDown();
            removed.get(10,TimeUnit.SECONDS);
            assertNull(sql.fetchOne("select status from user_authorization_state where user_id=?",user));
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
