package com.walmartlabs.concord.it.testingserver;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.process.ExecutionIdentity;
import com.walmartlabs.concord.server.process.ProcessKind;
import com.walmartlabs.concord.server.process.queue.ProcessQueueDao;
import com.walmartlabs.concord.server.process.ProcessSecurityContext;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.ProcessStatus;
import com.walmartlabs.concord.server.security.Roles;
import com.walmartlabs.concord.server.plugins.oidc.OidcPluginModule;
import com.walmartlabs.concord.server.plugins.pfedsso.PluginModule;
import com.walmartlabs.concord.server.security.ldap.LdapPluginModule;
import com.walmartlabs.concord.server.security.ldap.UserLdapGroupSynchronizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import javax.naming.Context;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.InitialDirContext;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityProvidersIT {
    private static final String BASE = "dc=example,dc=org";
    private static final String USER_DN = "uid=alice,ou=users," + BASE;
    private static final String GROUP_DN = "cn=developers,ou=groups," + BASE;
    private static final String WHOAMI = "/api/service/console/whoami";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    @Timeout(180)
    public void pfedUsesLdapDirectoryAndGrantsWithoutLdapPasswordLogin() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
             var ldap = new GenericContainer<>(DockerImageName.parse("osixia/openldap:1.5.0"))
                     .withEnv("LDAP_DOMAIN", "example.org")
                     .withEnv("LDAP_ADMIN_PASSWORD", "admin")
                     // The bootstrap slapd listens before the memberOf overlay is installed.
                     .withExposedPorts(389)
                     .waitingFor(Wait.forLogMessage(".*slapd starting.*\\n", 1));
             var protocol = new SecurityProtocolFixture()) {
            db.start();
            ldap.start();
            String ldapUrl = "ldap://" + ldap.getHost() + ":" + ldap.getMappedPort(389);
            populateLdap(ldapUrl);

            Map<String, Object> config = new HashMap<>();
            config.put("ldap.url", ldapUrl);
            config.put("ldap.systemPassword", "admin");
            config.put("ldap.authenticationEnabled", false);
            config.put("ldapGroupSync.fetchLimit", 1);
            config.put("sso.pfed.enabled", true);
            config.put("sso.pfed.bearerToken.enableBearerTokens", true);
            config.put("sso.pfed.bearerToken.allowAllClientIds", false);
            config.put("sso.pfed.bearerToken.allowedClientIds", List.of("test-client"));
            config.put("sso.tokenSignatureValidation", true);
            config.put("sso.tokenSigningKeyUrl", protocol.baseUrl() + "/jwks");
            config.put("sso.userInfoEndpointUrl", protocol.baseUrl() + "/userinfo");
            config.put("sso.identityAuthority", protocol.baseUrl());
            config.put("sso.profileProviderId", "ldap");
            config.put("sso.identityDirectory", "ldap");
            config.put("sso.authorizationSources", List.of("ldap"));
            UUID userId;


            try (var server = new TestingConcordServer(db, freePort(), config,
                    List.of(c -> new LdapPluginModule(), c -> new PluginModule()))) {
                server.start();
                String token = protocol.signedToken("alice");
                var login = get(server, WHOAMI, "Bearer " + token);
                assertEquals(200, login.statusCode(), login.body());
                assertEquals("alice", JSON.readTree(login.body()).path("username").asText());

                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword())) {
                    try (var query = connection.prepareStatement("select user_id from user_identities where provider_id='sso' and authority=? and subject='alice' and is_active")) {
                        query.setString(1, protocol.baseUrl());
                        try (var rows = query.executeQuery()) {
                            assertTrue(rows.next());
                            userId = rows.getObject(1, UUID.class);
                            assertFalse(rows.next());
                        }
                    }
                    try (var query = connection.prepareStatement("select i.provider_id, b.source_id, s.status from user_authorization_bindings b join user_identities i on i.identity_id=b.identity_id join user_authorization_state s on s.user_id=b.user_id and s.source_id=b.source_id where b.user_id=?")) {
                        query.setObject(1, userId);
                        try (var rows = query.executeQuery()) {
                            assertTrue(rows.next());
                            assertEquals("ldap", rows.getString(1));
                            assertEquals("ldap", rows.getString(2));
                            assertEquals("VALID", rows.getString(3));
                            assertFalse(rows.next());
                        }
                    }
                    try (var query = connection.prepareStatement("select group_id from v_effective_source_groups where user_id=? and source_id='ldap'")) {
                        query.setObject(1, userId);
                        try (var rows = query.executeQuery()) {
                            assertTrue(rows.next(), "LDAP membership must be observed");
                            assertEquals(GROUP_DN.toLowerCase(), rows.getString(1).toLowerCase());
                            assertFalse(rows.next(), "userinfo's forged memberOf must not become an LDAP grant");
                        }
                    }
                }

                // Exercise the real PostgreSQL age comparison and the subsequent keyset page.
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var update = connection.prepareStatement("update user_authorization_state set observed_at = '2000-01-01 00:00:00+00' where user_id=? and source_id='ldap'")) {
                    update.setObject(1, userId);
                    assertEquals(1, update.executeUpdate());
                }
                server.getServer().getInjector().getInstance(UserLdapGroupSynchronizer.class).performTask();
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var query = connection.prepareStatement("select observed_at > '2000-01-02 00:00:00+00' from user_authorization_state where user_id=? and source_id='ldap'")) {
                    query.setObject(1, userId);
                    try (var rows = query.executeQuery()) {
                        assertTrue(rows.next());
                        assertTrue(rows.getBoolean(1), "stale LDAP state must be refreshed");
                    }
                }

                // Exercise the grant through the public API, then revoke it while the
                // same signed credential is still valid. No new login is needed to
                // make a removed source mapping ineffective.
                String roleGroups = "/api/v2/role/concordAdmin/groups/ldap";
                var mapped = put(server, roleGroups + "?replace=true", server.getAdminApiKey(),
                        JSON.writeValueAsString(List.of(GROUP_DN)));
                assertEquals(200, mapped.statusCode(), mapped.body());
                var allowed = get(server, roleGroups, "Bearer " + token);
                assertEquals(200, allowed.statusCode(), allowed.body());
                // A suspended queue entry exercises the durable process actor without
                // scheduling an asynchronous worker during the subsequent restart.
                var injector = server.getServer().getInjector();
                var processKey = new ProcessKey(UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
                var queue = injector.getInstance(ProcessQueueDao.class);
                queue.tx(tx -> queue.insert(tx, processKey, ProcessStatus.SUSPENDED, ProcessKind.DEFAULT,
                        null, null, null, null, null, userId, Map.of(), null));
                var processSecurity = injector.getInstance(ProcessSecurityContext.class);
                processSecurity.storeIdentity(processKey, new ExecutionIdentity(userId, "sso"));
                assertTrue(processSecurity.runAsCurrentUser(processKey, Roles::isAdmin));
                var revoked = put(server, roleGroups + "?replace=true", server.getAdminApiKey(), "[]");
                assertEquals(200, revoked.statusCode(), revoked.body());
                var denied = get(server, roleGroups, "Bearer " + token);
                assertEquals(403, denied.statusCode(), denied.body());
                assertFalse(processSecurity.runAsCurrentUser(processKey, Roles::isAdmin));

                String[] parts = protocol.signedToken("mallory").split("\\.");
                String forged = parts[0] + "." + parts[1] + "." + (parts[2].charAt(0) == 'A' ? 'B' : 'A') + parts[2].substring(1);
                assertNotEquals(200, get(server, WHOAMI, "Bearer " + forged).statusCode());
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var query = connection.prepareStatement("select (select count(*) from user_identities where subject='mallory') + (select count(*) from users where username='mallory')");
                     var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
                String basic = Base64.getEncoder().encodeToString("alice:alice".getBytes(StandardCharsets.UTF_8));
                assertNotEquals(200, get(server, WHOAMI, "Basic " + basic).statusCode());
            }

            protocol.email("alice");
            Map<String, Object> oidcConfig = new HashMap<>();
            oidcConfig.put("ldap.url", ldapUrl);
            oidcConfig.put("ldap.systemPassword", "admin");
            oidcConfig.put("ldap.authenticationEnabled", false);
            oidcConfig.put("oidc.enabled", true);
            oidcConfig.put("oidc.discoveryUri", protocol.baseUrl() + "/discovery");
            oidcConfig.put("oidc.profileProviderId", "local");
            oidcConfig.put("oidc.authorizationSources", List.of("oidc"));
            try (var server = new TestingConcordServer(db, freePort(), oidcConfig,
                    List.of(c -> new LdapPluginModule(), c -> new OidcPluginModule()))) {
                server.start();
                var login = get(server, WHOAMI, "Bearer " + protocol.signedToken("alice"));
                assertEquals(200, login.statusCode(), login.body());
                assertEquals("alice", JSON.readTree(login.body()).path("username").asText());
                assertEquals("oidc", JSON.readTree(login.body()).path("realm").asText());
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var query = connection.prepareStatement("select user_id from user_identities where provider_id='oidc' and authority=? and subject='alice' and is_active")) {
                    query.setString(1, protocol.baseUrl());
                    try (var rows = query.executeQuery()) {
                        assertTrue(rows.next());
                        assertNotEquals(userId, rows.getObject(1, UUID.class));
                        assertFalse(rows.next());
                    }
                }
            }
        }
    }

    @Test
    @Timeout(180)
    public void oidcUsesVerifiedUserinfoAndItsOwnAuthorizationSource() throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
             var protocol = new SecurityProtocolFixture()) {
            db.start();
            Map<String, Object> config = Map.of(
                    "oidc.enabled", true,
                    "oidc.discoveryUri", protocol.baseUrl() + "/discovery",
                    "oidc.profileProviderId", "local",
                    "oidc.authorizationSources", List.of("oidc"));
            try (var server = new TestingConcordServer(db, freePort(), config, List.of(c -> new OidcPluginModule()))) {
                server.start();
                var login = get(server, WHOAMI, "Bearer " + protocol.signedToken("alice"));
                assertEquals(200, login.statusCode(), login.body());
                try (var connection = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
                     var query = connection.prepareStatement("select i.provider_id, i.authority, i.subject, b.source_id, s.status from user_identities i join user_authorization_bindings b on b.user_id=i.user_id join user_authorization_state s on s.user_id=b.user_id and s.source_id=b.source_id where i.provider_id='oidc' and i.subject='alice' and i.is_active");
                     var rows = query.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("oidc", rows.getString(1));
                    assertEquals(protocol.baseUrl(), rows.getString(2));
                    assertEquals("alice", rows.getString(3));
                    assertEquals("oidc", rows.getString(4));
                    assertEquals("VALID", rows.getString(5));
                    assertFalse(rows.next());
                }
                assertNotEquals(200, get(server, WHOAMI, "Bearer invalid-token").statusCode());
            }
        }
    }

    private HttpResponse<String> get(TestingConcordServer server, String path, String authorization) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.getApiBaseUrl() + path))
                .header("Authorization", authorization).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(TestingConcordServer server, String path, String authorization, String json) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(server.getApiBaseUrl() + path))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int freePort() throws Exception {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void populateLdap(String url) throws Exception {
        Properties properties = new Properties();
        properties.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        properties.put(Context.PROVIDER_URL, url);
        properties.put(Context.SECURITY_AUTHENTICATION, "simple");
        properties.put(Context.SECURITY_PRINCIPAL, "cn=admin," + BASE);
        properties.put(Context.SECURITY_CREDENTIALS, "admin");
        var context = new InitialDirContext(properties);
        try {
            for (String ou : List.of("users", "groups")) {
                var attributes = new BasicAttributes(true);
                attributes.put("objectClass", "organizationalUnit");
                attributes.put("ou", ou);
                context.createSubcontext("ou=" + ou + "," + BASE, attributes).close();
            }
            var user = new BasicAttributes(true);
            Attribute classes = new BasicAttribute("objectClass");
            classes.add("top");
            classes.add("person");
            classes.add("organizationalPerson");
            classes.add("inetOrgPerson");
            user.put(classes);
            user.put("uid", "alice");
            user.put("cn", "alice");
            user.put("sn", "alice");
            user.put("mail", "alice@example.org");
            user.put("userPassword", "alice");
            context.createSubcontext(USER_DN, user).close();
            var group = new BasicAttributes(true);
            group.put("objectClass", "groupOfUniqueNames");
            group.put("cn", "developers");
            group.put("uniqueMember", USER_DN);
            context.createSubcontext(GROUP_DN, group).close();
            Attribute membership = context.getAttributes(USER_DN, new String[]{"memberOf"}).get("memberOf");
            assertNotNull(membership, "OpenLDAP memberOf overlay must populate the user");
            assertTrue(membership.contains(GROUP_DN));
        } finally {
            context.close();
        }
    }
}
