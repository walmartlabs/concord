package com.walmartlabs.concord.it.testingserver;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.server.security.ldap.LdapPluginModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SecurityCompatibilityIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ADMIN_ID = "230c5c9c-d9a7-11e6-bcfd-bb681c07b26c";
    private static final String TEAM = "/api/v1/org/Default/team/compatibility";
    private static final String SOURCE_GROUPS = "/api/v2/org/Default/team/compatibility/groups/ldap";

    @Test
    @Timeout(120)
    public void legacyWireAndExplicitApisWorkWhenEnabled() throws Exception {
        exercise(true);
    }

    @Test
    @Timeout(120)
    public void legacySelectorsAndGroupsAreRejectedWhenDisabled() throws Exception {
        exercise(false);
    }

    private void exercise(boolean legacyEnabled) throws Exception {
        try (var db = new PostgreSQLContainer<>("postgres:15-alpine");
             var ldap = new GenericContainer<>(DockerImageName.parse("osixia/openldap:1.5.0"))
                     .withEnv("LDAP_DOMAIN", "example.org")
                     .withEnv("LDAP_ADMIN_PASSWORD", "admin")
                     .withExposedPorts(389);
             var socket = new ServerSocket(0)) {
            db.start();
            ldap.start();
            int port = socket.getLocalPort();
            socket.close();
            var config = Map.<String, Object>of("security.legacy.enabled", legacyEnabled,
                    "ldap.url", "ldap://" + ldap.getHost() + ":" + ldap.getMappedPort(389),
                    "ldap.systemPassword", "admin", "ldap.authenticationEnabled", false);
            try (var server = new TestingConcordServer(db, port, config, List.of(c -> new LdapPluginModule()))) {
                server.start();
                var http = HttpClient.newHttpClient();
                request(http, server, "POST", "/api/v1/org/Default/team", "{\"name\":\"compatibility\"}", 200);

                if (legacyEnabled) {
                    JsonNode created = json(request(http, server, "POST", "/api/v1/user",
                            "{\"username\":\"legacy-wire\",\"userType\":\"LOCAL\",\"displayName\":\"Legacy Wire\"}", 200));
                    assertFalse(created.path("id").asText().isEmpty());
                    JsonNode fetched = json(request(http, server, "GET", "/api/v1/user/legacy-wire", null, 200));
                    assertEquals(created.path("id").asText(), fetched.path("id").asText());
                    assertEquals("LOCAL", fetched.path("type").asText());
                } else {
                    request(http, server, "GET", "/api/v1/user/admin", null, 400);
                    request(http, server, "POST", "/api/v1/user",
                            "{\"username\":\"legacy-wire\",\"userType\":\"LOCAL\",\"displayName\":\"Legacy Wire\"}", 400);
                }

                String legacyUser = "[{\"username\":\"admin\",\"userType\":\"LOCAL\",\"role\":\"MEMBER\"}]";
                request(http, server, "PUT", TEAM + "/users", legacyUser, legacyEnabled ? 200 : 400);
                if (legacyEnabled) {
                    assertTeamContains(http, server, ADMIN_ID);
                    request(http, server, "PUT", TEAM + "/ldapGroups",
                            "[{\"group\":\"legacy-group\",\"role\":\"MEMBER\"}]", 200);
                    JsonNode oldGroups = json(request(http, server, "GET", TEAM + "/ldapGroups", null, 200));
                    assertEquals("legacy-group", oldGroups.get(0).path("group").asText());
                    JsonNode sourceGroups = json(request(http, server, "GET", SOURCE_GROUPS, null, 200));
                    assertEquals("legacy-group", sourceGroups.get(0).path("groupId").asText());
                } else {
                    request(http, server, "GET", TEAM + "/ldapGroups", null, 400);
                    request(http, server, "PUT", TEAM + "/ldapGroups",
                            "[{\"group\":\"legacy-group\",\"role\":\"MEMBER\"}]", 400);
                }

                JsonNode sources = json(request(http, server, "GET", "/api/v2/security/sources", null, 200));
                assertTrue(sources.isArray());
                assertTrue(contains(sources, "sourceId", "ldap"));
                request(http, server, "PUT", SOURCE_GROUPS + "?replace=true",
                        "[{\"groupId\":\"qualified-group\",\"role\":\"MEMBER\"}]", 200);
                JsonNode groups = json(request(http, server, "GET", SOURCE_GROUPS, null, 200));
                assertEquals(1, groups.size());
                assertEquals("qualified-group", groups.get(0).path("groupId").asText());

                String identity = "{\"providerId\":\"compatibility-test\",\"authority\":\"issuer\",\"subject\":\"opaque-subject\"}";
                String identitiesPath = "/api/v2/user/" + ADMIN_ID + "/identities";
                JsonNode linked = json(request(http, server, "POST", identitiesPath, identity, 200));
                String identityId = linked.path("identityId").asText();
                assertFalse(identityId.isEmpty());
                assertEquals("opaque-subject", linked.path("identity").path("subject").asText());
                JsonNode identities = json(request(http, server, "GET", identitiesPath, null, 200));
                assertTrue(contains(identities, "identityId", identityId));
                request(http, server, "PUT", TEAM + "/users?replace=true",
                        "[{\"identity\":" + identity + ",\"role\":\"MEMBER\"}]", 200);
                assertTeamContains(http, server, ADMIN_ID);
                request(http, server, "PUT", TEAM + "/users?replace=true",
                        "[{\"userId\":\"" + ADMIN_ID + "\",\"role\":\"MEMBER\"}]", 200);
                assertTeamContains(http, server, ADMIN_ID);
                assertTrue(json(request(http, server, "GET", "/api/v2/user/" + ADMIN_ID + "/authorization", null, 200)).isArray());
                request(http, server, "DELETE", identitiesPath + "/" + identityId, null, 204);
            }
        }
    }

    private static void assertTeamContains(HttpClient http, TestingConcordServer server, String id) throws Exception {
        JsonNode members = json(request(http, server, "GET", TEAM + "/users", null, 200));
        assertTrue(contains(members, "userId", id), members.toString());
    }

    private static boolean contains(JsonNode array, String field, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) return true;
        }
        return false;
    }

    private static JsonNode json(String body) throws Exception {
        return JSON.readTree(body);
    }

    private static String request(HttpClient http, TestingConcordServer server, String method, String path,
                                  String body, int expectedStatus) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(server.getApiBaseUrl() + path))
                .header("Authorization", server.getAdminApiKey())
                .header("Accept", "application/json");
        if (body != null) builder.header("Content-Type", "application/json");
        var response = http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), method + " " + path + ": " + response.body());
        return response.body();
    }
}
