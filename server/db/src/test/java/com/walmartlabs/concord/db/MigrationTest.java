package com.walmartlabs.concord.db;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

public class MigrationTest {

    private static String dbImage;

    @BeforeAll
    public static void setUp() throws Exception {
        var props = new Properties();
        props.load(MigrationTest.class.getClassLoader().getResourceAsStream("db.properties"));
        dbImage = props.getProperty("db.image");
    }

    @SuppressWarnings("resource")
    private PostgreSQLContainer<?> createDbContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse(dbImage)
                .asCompatibleSubstituteFor("postgres"))
                .waitingFor(Wait.forListeningPort());
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.MINUTES)
    public void regularMigration() {
        try (var db = createDbContainer()) {
            db.start();
            applyMigrations(db);
        }
    }

    private static final UUID LOCAL = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LDAP = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID UNICODE = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID SSO = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID OAUTH = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID SSO_OTHER = UUID.fromString("10000000-0000-0000-0000-000000000009");
    private static final UUID ROLE = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID TEAM = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    @Timeout(value = 3, unit = TimeUnit.MINUTES)
    public void concurrentMigrations() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            migrateBeforeSecurity(db);
            fixture(db);

            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> applyMigrations(db));
                var second = executor.submit(() -> applyMigrations(db));
                first.get(2, TimeUnit.MINUTES);
                second.get(2, TimeUnit.MINUTES);
            } finally {
                executor.shutdownNow();
            }

            assertBackfill(db);
            var before = snapshot(db);
            applyMigrations(db);
            assertEquals(before, snapshot(db));
            assertEquals(List.of("2461000", "2461010", "2461020"), rows(db,
                    "select id from server_db_log where id in ('2461000','2461010','2461020') order by id"));
            execute(db, "delete from users where user_id='" + LDAP + "'");
            assertEquals(List.of("<null>|false"), rows(db, "select user_id,is_active from user_identities where provider_id='ldap' and authority='' and subject='[\"shared\",\"example\"]'"));
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void identityPersistenceAllowsDuplicateLegacyNamesAndNullType() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            applyMigrations(db);
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            execute(db, "insert into users (user_id, username, user_type) values ('" + first + "', 'identity-test', null), ('" + second + "', 'identity-test', null)");
            assertEquals(List.of("2"), rows(db, "select count(*) from users where username='identity-test' and user_type is null"));
            UUID third = UUID.randomUUID();
            UUID fourth = UUID.randomUUID();
            execute(db, "insert into users (user_id, username, domain, user_type) values ('" + third + "', 'identity-legacy', 'example', 'LOCAL'), ('" + fourth + "', 'identity-legacy', 'example', 'LOCAL')");
            assertEquals(List.of("2"), rows(db, "select count(*) from users where username='identity-legacy'"));
            assertEquals(List.of("YES|<null>"), rows(db, "select is_nullable,column_default from information_schema.columns where table_name='users' and column_name='user_type'"));
        }
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.MINUTES)
    public void canonicalCollisionRollsBackEntireBackfill() throws Exception {
        try (var db = createDbContainer()) {
            db.start();
            migrateBeforeSecurity(db);
            fixture(db);
            UUID other = UUID.fromString("10000000-0000-0000-0000-000000000006");
            UUID third = UUID.fromString("10000000-0000-0000-0000-000000000007");
            // PostgreSQL's existing unique index permits multiple null-domain rows.
            user(db, other, "STRASSE", null, "LDAP", false);
            user(db, third, "strasse", null, "LDAP", false);
            UUID fourth = UUID.fromString("10000000-0000-0000-0000-000000000008");
            user(db, fourth, "Strasse", null, "LDAP", false);

            Exception failure = assertThrows(Exception.class, () -> updateDirect(db, false));
            StringBuilder messages = new StringBuilder();
            for (Throwable t = failure; t != null; t = t.getCause()) {
                messages.append(t.getMessage()).append('\n');
            }
            for (UUID id : List.of(other, third, fourth)) {
                assertTrue(messages.toString().contains(id.toString()), messages.toString());
            }
            assertEquals(List.of("0"), rows(db, "select count(*) from legacy_user_enrollment"));
            assertEquals(List.of("0"), rows(db, "select count(*) from user_identities"));
            assertEquals(List.of("0"), rows(db, "select count(*) from legacy_identity_adoptions"));
            assertEquals(List.of("0"), rows(db, "select count(*) from user_authorization_bindings"));
            assertEquals(List.of("0"), rows(db, "select count(*) from user_authorization_state"));
            assertEquals(List.of("0"), rows(db, "select count(*) from user_source_groups"));
            assertEquals(List.of("0"), rows(db, "select count(*) from role_source_groups"));
            assertEquals(List.of("0"), rows(db, "select count(*) from team_source_groups"));
            assertEquals(List.of("0"), rows(db, "select count(*) from users where profile_provider_id is not null or profile_identity_id is not null"));
            assertEquals(List.of("0"), rows(db, "select count(*) from server_db_log where id = '2461010'"));
        }
    }

    private void migrateBeforeSecurity(PostgreSQLContainer<?> db) throws Exception {
        updateDirect(db, true);
    }

    private void updateDirect(PostgreSQLContainer<?> container, boolean beforeSecurity) throws Exception {
        var cfg = new DatabaseConfigurationImpl(container.getJdbcUrl(), container.getUsername(), container.getPassword());
        try (Connection conn = DriverManager.getConnection(cfg.url(), cfg.username(), cfg.password())) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(conn));
            var provider = new MainDBChangeLogProvider();
            database.setDatabaseChangeLogTableName(provider.getChangeLogTable());
            database.setDatabaseChangeLogLockTableName(provider.getLockTable());
            try (var lb = new Liquibase(provider.getChangeLogPath(), new ClassLoaderResourceAccessor(), database)) {
                cfg.changeLogParameters().forEach(lb::setChangeLogParameter);
                if (beforeSecurity) {
                    var pending = lb.listUnrunChangeSets(new Contexts(), new LabelExpression());
                    int boundary = -1;
                    for (int i = 0; i < pending.size(); i++) {
                        if ("2461000".equals(pending.get(i).getId())) {
                            boundary = i;
                            break;
                        }
                    }
                    assertTrue(boundary > 0, "security changeset not found");
                    lb.update(boundary, new Contexts(), new LabelExpression());
                } else {
                    lb.update(new Contexts(), new LabelExpression());
                }
            }
        }
    }

    private void fixture(PostgreSQLContainer<?> db) throws SQLException {
        user(db, LOCAL, "Shared", "Example", "LOCAL", false);
        user(db, LDAP, "Shared", "Example", "LDAP", true);
        user(db, UNICODE, "İUSER", null, "LDAP", false);
        user(db, SSO, "Shared", "Example", "SSO", false);
        user(db, SSO_OTHER, "Shared", null, "SSO", false);
        user(db, OAUTH, "person@example.com", null, "LOCAL", false);
        execute(db, "update users set last_group_sync_dt = '2020-01-02T03:04:05Z' where user_id = '" + LDAP + "'");
        execute(db, "insert into roles(role_id, role_name) values ('" + ROLE + "', 'fixture-role')");
        execute(db, "insert into user_roles(user_id, role_id) values ('" + LDAP + "','" + ROLE + "')");
        execute(db, "insert into user_teams(user_id, team_id, team_role) values ('" + LDAP + "','" + TEAM + "','OWNER')");
        execute(db, "insert into user_ldap_groups(user_id, ldap_group) values ('" + LDAP + "','cn=fixture'),('" + LDAP + "','cn=fixture')");
        execute(db, "insert into role_ldap_groups(role_id, ldap_group) values ('" + ROLE + "','cn=fixture')");
        execute(db, "insert into team_ldap_groups(team_id, ldap_group, team_role) values ('" + TEAM + "','cn=fixture','MEMBER')");
    }

    private void user(PostgreSQLContainer<?> db, UUID id, String name, String domain, String type, boolean disabled) throws SQLException {
        try (Connection c = connection(db);
             var ps = c.prepareStatement("insert into users(user_id, username, domain, user_type, is_disabled) values (?,?,?,?,?)")) {
            ps.setObject(1, id);
            ps.setString(2, name);
            ps.setString(3, domain);
            ps.setString(4, type);
            ps.setBoolean(5, disabled);
            ps.executeUpdate();
        }
    }

    private void assertBackfill(PostgreSQLContainer<?> db) throws SQLException {
        assertEquals(List.of(LOCAL + "|local||" + LOCAL, LDAP + "|ldap||[\"shared\",\"example\"]",
                        UNICODE + "|ldap||[\"i̇user\",null]", OAUTH + "|local||" + OAUTH),
                rows(db, "select user_id,provider_id,authority,subject from user_identities where user_id in ('" +
                        LOCAL + "','" + LDAP + "','" + UNICODE + "','" + SSO + "','" + OAUTH + "') order by user_id"));
        assertEquals(List.of(LDAP + "|ldap||[\"shared\",\"example\"]", UNICODE + "|ldap||[\"i̇user\",null]"),
                rows(db, "select user_id,provider_id,authority,subject from legacy_identity_adoptions where user_id in ('" + LDAP + "','" + UNICODE + "') order by user_id"));
        assertEquals(List.of(LOCAL + "|Shared|Example|LOCAL", LDAP + "|Shared|Example|LDAP",
                        UNICODE + "|İUSER|<null>|LDAP", SSO + "|Shared|Example|SSO", OAUTH + "|person@example.com|<null>|LOCAL",
                        SSO_OTHER + "|Shared|<null>|SSO"),
                rows(db, "select user_id,username,domain,user_type from legacy_user_enrollment where user_id in ('" +
                        LOCAL + "','" + LDAP + "','" + UNICODE + "','" + SSO + "','" + OAUTH + "','" + SSO_OTHER + "') order by user_id"));
        assertEquals(List.of("true|ldap|true"), rows(db, "select u.is_disabled, u.profile_provider_id, i.user_id = u.user_id from users u join user_identities i on i.identity_id = u.profile_identity_id where u.user_id = '" + LDAP + "'"));
        assertEquals(List.of("ldap"), rows(db, "select profile_provider_id from users where user_id = '" + SSO + "'"));
        assertEquals(List.of("0"), rows(db, "select count(*) from user_identities where user_id in ('" + SSO + "','" + SSO_OTHER + "')"));
        assertEquals(List.of("true"), rows(db, "select b.user_id = i.user_id from user_authorization_bindings b join user_identities i on i.identity_id=b.identity_id where b.user_id='" + LDAP + "' and b.source_id='ldap'"));
        assertEquals(List.of("local"), rows(db, "select profile_provider_id from users where user_id = '" + OAUTH + "'"));
        assertEquals(List.of("ldap|false"), rows(db, "select source_id,is_active from authorization_sources where source_id = 'ldap'"));
        assertEquals(List.of("ldap|DEFAULT|STALE|true"),
                rows(db, "select b.source_id,b.origin,s.status,s.observed_at = u.last_group_sync_dt from user_authorization_bindings b join user_authorization_state s on s.binding_id=b.binding_id join users u on u.user_id=b.user_id where b.user_id='" + LDAP + "'"));
        assertEquals(List.of("ldap|cn=fixture"), rows(db, "select source_id,group_id from user_source_groups where user_id='" + LDAP + "'"));
        assertEquals(List.of("ldap|cn=fixture"), rows(db, "select source_id,group_id from role_source_groups where role_id='" + ROLE + "'"));
        assertEquals(List.of("ldap|cn=fixture|MEMBER"), rows(db, "select source_id,group_id,team_role from team_source_groups where team_id='" + TEAM + "'"));
        assertEquals(List.of(ROLE.toString()), rows(db, "select role_id from user_roles where user_id='" + LDAP + "'"));
        assertEquals(List.of(TEAM + "|OWNER"), rows(db, "select team_id,team_role from user_teams where user_id='" + LDAP + "'"));
    }

    private List<List<String>> snapshot(PostgreSQLContainer<?> db) throws SQLException {
        List<List<String>> result = new ArrayList<>();
        for (String table : List.of("users", "user_identities", "legacy_user_enrollment", "legacy_identity_adoptions",
                "authorization_sources", "user_authorization_bindings", "user_authorization_state",
                "user_source_groups", "role_source_groups", "team_source_groups", "user_source_roles", "user_source_teams")) {
            result.add(rows(db, "select row_to_json(t)::text from (select * from " + table + " order by 1,2) t"));
        }
        return result;
    }

    private Connection connection(PostgreSQLContainer<?> db) throws SQLException {
        return DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
    }

    private void execute(PostgreSQLContainer<?> db, String sql) throws SQLException {
        try (var c = connection(db); var s = c.createStatement()) {
            s.executeUpdate(sql);
        }
    }

    private List<String> rows(PostgreSQLContainer<?> db, String sql) throws SQLException {
        try (var c = connection(db); var s = c.createStatement(); var rs = s.executeQuery(sql)) {
            List<String> result = new ArrayList<>();
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= columns; i++) {
                    Object value = rs.getObject(i);
                    values.add(value == null ? "<null>" : value.toString());
                }
                result.add(String.join("|", values));
            }
            return result;
        }
    }

    private void applyMigrations(PostgreSQLContainer<?> db) {
        var cfg = new DatabaseConfigurationImpl(db.getJdbcUrl(), db.getUsername(), db.getPassword());
        DataSourceUtils.migrateDb(cfg, new MainDBChangeLogProvider());
    }

    private static String base64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(UTF_8));
    }

    private static final class DatabaseConfigurationImpl implements DatabaseConfiguration {

        private final String url;
        private final String username;
        private final String password;

        private DatabaseConfigurationImpl(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public String url() {
            return url;
        }

        @Override
        public String username() {
            return username;
        }

        @Override
        public String password() {
            return password;
        }

        @Override
        public int maxPoolSize() {
            return 10;
        }

        @Override
        public Duration maxLifetime() {
            return Duration.ofMinutes(30);
        }

        @Override
        public Map<String, Object> changeLogParameters() {
            return Map.of(
                    "createExtensionAvailable", "true",
                    "defaultAdminToken", base64("foobar"),
                    "skipAdminTokenGeneration", "false",
                    "defaultAgentToken", base64("barbaz"),
                    "skipAgentTokenGeneration", "false",
                    "secretStoreSalt", base64("foo"),
                    "serverPassword", base64("bar"));
        }
    }
}
