package com.walmartlabs.concord.server.org.policy;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.DatabaseConfiguration;
import com.walmartlabs.concord.db.DatabaseModule;
import com.walmartlabs.concord.db.MainDBChangeLogProvider;
import com.walmartlabs.concord.server.ConcordObjectMapper;
import com.walmartlabs.concord.server.UuidGenerator;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class PolicyDaoIntegrationTest {
    private static PostgreSQLContainer<?> database;
    private static DataSource dataSource;
    private static DSLContext sql;
    private static PolicyDao dao;

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
        dao = new PolicyDao(jooq, new ConcordObjectMapper(new ObjectMapper()), new UuidGenerator());
    }

    private static String encoded(String s) { return Base64.getEncoder().encodeToString(s.getBytes(UTF_8)); }

    @AfterAll
    static void stop() throws Exception {
        if (dataSource != null) dataSource.getClass().getMethod("close").invoke(dataSource);
        if (database != null) database.stop();
    }

    @Test
    void listAndUnlinkAreRestrictedToUserLevelLinks() {
        UUID user = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        sql.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            tx.execute("insert into users(user_id,username,user_type,is_disabled) values (?,?,?,?)", user, "policy-user-" + user, "LOCAL", false);
            tx.execute("insert into organizations(org_id,org_name) values (?,?)", org, "policy-org-" + org);
        });
        UUID policy = dao.insert("policy-" + UUID.randomUUID(), null, Map.of());
        UUID systemPolicy = dao.insert("system-" + UUID.randomUUID(), null, Map.of());
        dao.link(policy, null, null, user);
        dao.link(policy, org, null, user);
        dao.link(systemPolicy, null, null, null);

        assertEquals(1, dao.listByUser(user).size());
        assertEquals(policy, dao.listByUser(user).get(0).id());
        assertTrue(dao.listByUser(UUID.randomUUID()).isEmpty());
        dao.unlinkUser(policy, user);
        assertTrue(dao.listByUser(user).isEmpty());
        assertEquals(1, sql.fetchCount(sql.selectFrom(DSL.table("policy_links"))
                .where(DSL.field("policy_id").eq(policy)).and(DSL.field("org_id").eq(org)).and(DSL.field("user_id").eq(user))));
        assertEquals(1, sql.fetchCount(sql.selectFrom(DSL.table("policy_links"))
                .where(DSL.field("policy_id").eq(systemPolicy))));
    }
}
