package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ProcessCardIT extends AbstractServerIT {

    @Test
    public void testInvalidRequests() throws Exception {
        ProcessCardsApi api = new ProcessCardsApi(getApiClient());

        // no cards
        List<ProcessCardEntry> cards = api.listUserProcessCards();
        assertNotNull(cards);
        assertTrue(cards.isEmpty());

        // unknown card id -> 404 for form
        try {
            api.getProcessCardForm(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        // unknown card id -> 404 for form data
        try {
            api.getProcessCardFormData(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }

        // unknown card id -> 404 for delete card
        try {
            api.deleteProcessCard(UUID.randomUUID());
            fail("exception expected");
        } catch (ApiException e) {
            assertEquals(404, e.getCode());
        }
    }

    @Test
    public void testCRUD() throws Exception {
        ProcessCardsApi api = new ProcessCardsApi(getApiClient());

        withOrg(orgName -> {
            withProject(orgName, projectName -> {
                var r = new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                        .org(orgName)
                        .project(projectName)
                        .name("test")
                        .description("test description")
                        .data(Map.of("myKey", "myValue"))
                        .form("hello world")
                        .icon("test icon");

                // create card
                UUID cardId = api.createOrUpdateProcessCard(r.asMap()).getId();

                ProcessCardEntry entry = api.getProcessCard(cardId);
                assertEquals(orgName, entry.getOrgName());
                assertEquals(projectName, entry.getProjectName());
                assertEquals("test", entry.getName());
                assertEquals("test description", entry.getDescription());

                // -- list cards
                List<ProcessCardEntry> cards = api.listUserProcessCards();
                assertEquals(1, cards.size());

                try (InputStream is = api.getProcessCardForm(cardId)) {
                    assertEquals("hello world", new String(is.readAllBytes(), StandardCharsets.UTF_8));
                }
                ApiResponse<InputStream> formResponse = api.getProcessCardFormWithHttpInfo(cardId);
                try (InputStream ignored = formResponse.getData()) {
                    String csp = formResponse.getHeaders().entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase("Content-Security-Policy"))
                            .flatMap(e -> e.getValue().stream()).findFirst().orElseThrow();
                    assertTrue(csp.contains("sandbox;"), csp);
                    assertFalse(csp.contains("allow-same-origin"), csp);
                    assertTrue(csp.contains("default-src 'none'"), csp);
                    assertTrue(formResponse.getHeaders().entrySet().stream()
                            .filter(e -> e.getKey().equalsIgnoreCase("Content-Type"))
                            .flatMap(e -> e.getValue().stream()).anyMatch(v -> v.startsWith("text/plain")));
                }

                try (InputStream is = api.getProcessCardFormData(cardId)) {
                    String data = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    assertTrue(data.startsWith("data = "), data);
                    assertTrue(data.contains("myKey"), data);
                    assertTrue(data.contains("myValue"), data);
                }

                // update card
                r = new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                        .id(cardId)
                        .org(orgName)
                        .project(projectName)
                        .name("test update")
                        .description("test description update");

                UUID cardIdAfterUpdate = api.createOrUpdateProcessCard(r.asMap()).getId();
                assertEquals(cardId, cardIdAfterUpdate);

                entry = api.getProcessCard(cardId);
                assertEquals(orgName, entry.getOrgName());
                assertEquals(projectName, entry.getProjectName());
                assertEquals("test update", entry.getName());
                assertEquals("test description update", entry.getDescription());

                // delete card
                api.deleteProcessCard(cardId);
            });
        });
    }

    @Test
    public void testAuthorizationAndRevocation() throws Exception {
        UsersApi users = new UsersApi(getApiClient());
        String username = "card_reader_" + randomString();
        UUID userId = users.createOrUpdateUser(new CreateUserRequest().username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL)).getId();
        try {
            String key = new ApiKeysApi(getApiClient()).createUserApiKey(new CreateApiKeyRequest().userId(userId)).getKey();
            ApiClient readerClient = getApiClientForKey(key);
            ProcessCardsApi reader = new ProcessCardsApi(readerClient);
            ProcessCardsApi admin = new ProcessCardsApi(getApiClient());
            ProjectsApi projects = new ProjectsApi(getApiClient());
            TeamsApi teams = new TeamsApi(getApiClient());
            withOrg(orgName -> withProject(orgName, projectName -> {
                String teamName = "readers";
                UUID teamId = teams.createOrUpdateTeam(orgName, new TeamEntry().name(teamName)).getId();
                teams.addUsersToTeam(orgName, teamName, false, List.of(new TeamUserEntry()
                        .username(username).role(TeamUserEntry.RoleEnum.MEMBER)));
                projects.createOrUpdateProject(orgName, new ProjectEntry().name(projectName)
                        .visibility(ProjectEntry.VisibilityEnum.PRIVATE)
                        .processExecMode(ProjectEntry.ProcessExecModeEnum.WRITERS));
                UUID cardId = admin.createOrUpdateProcessCard(new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                        .org(orgName).project(projectName).name("restricted").form("<script>document.cookie</script>").asMap()).getId();

                assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCard(cardId)).getCode());
                assertEquals(400, assertThrows(ApiException.class, () -> admin.processCardAccess(cardId,
                        new ProcessCardAccessEntry().userIds(List.of(userId)))).getCode());
                projects.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                        .teamId(teamId).level(ResourceAccessEntry.LevelEnum.READER));
                // Project access alone is not a card share.
                assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCard(cardId)).getCode());
                admin.processCardAccess(cardId, new ProcessCardAccessEntry().userIds(List.of(userId)));
                assertEquals(cardId, reader.getProcessCard(cardId).getId());
                assertTrue(reader.listUserProcessCards().stream().anyMatch(c -> cardId.equals(c.getId())));
                assertEquals(403, assertThrows(ApiException.class, () -> reader.deleteProcessCard(cardId)).getCode());
                assertEquals(403, assertThrows(ApiException.class, () -> reader.processCardAccess(cardId,
                        new ProcessCardAccessEntry())).getCode());
                assertEquals(403, postStart(readerClient, cardId, "{}").statusCode());
                assertEquals(400, postStart(readerClient, cardId, "{\"value\":\"" + "x".repeat(65536) + "\"}").statusCode());

                projects.createOrUpdateProject(orgName, new ProjectEntry().name(projectName)
                        .processExecMode(ProjectEntry.ProcessExecModeEnum.READERS));
                HttpResponse<String> started = postStart(readerClient, cardId, "{\"project\":\"not-a-target\",\"name\":\"test\"}");
                assertEquals(200, started.statusCode(), started.body());
                assertTrue(started.body().contains("instanceId"), started.body());

                // Team delivery works too and is rechecked after permission revocation.
                admin.processCardAccess(cardId, new ProcessCardAccessEntry().teamIds(List.of(teamId)));
                assertEquals(cardId, reader.getProcessCard(cardId).getId());
                projects.updateProjectAccessLevelBulk(orgName, projectName, List.of());
                assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCard(cardId)).getCode());
                assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCardForm(cardId)).getCode());
                assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCardFormData(cardId)).getCode());
                assertFalse(reader.listUserProcessCards().stream().anyMatch(c -> cardId.equals(c.getId())));
                assertEquals(403, postStart(readerClient, cardId, "{}").statusCode());

                // Public-project membership confers READ, never authoring authority.
                projects.createOrUpdateProject(orgName, new ProjectEntry().name(projectName).visibility(ProjectEntry.VisibilityEnum.PUBLIC));
                assertEquals(403, assertThrows(ApiException.class, () -> reader.createOrUpdateProcessCard(
                        new ProcessCardsApi.CreateOrUpdateProcessCardRequest().org(orgName).project(projectName).name("unauthorized").asMap())).getCode());
                projects.updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                        .teamId(teamId).level(ResourceAccessEntry.LevelEnum.READER));
                admin.processCardAccess(cardId, new ProcessCardAccessEntry().userIds(List.of(userId)).teamIds(List.of(teamId)));
                withProject(orgName, newProject -> {
                    projects.updateProjectAccessLevel(orgName, newProject, new ResourceAccessEntry()
                            .teamId(teamId).level(ResourceAccessEntry.LevelEnum.READER));
                    admin.createOrUpdateProcessCard(new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                            .id(cardId).org(orgName).project(newProject).name("moved").asMap());
                    assertEquals(403, assertThrows(ApiException.class, () -> reader.getProcessCard(cardId)).getCode());
                    assertFalse(reader.listUserProcessCards().stream().anyMatch(c -> cardId.equals(c.getId())));
                });
            }));
        } finally {
            users.deleteUser(userId);
        }
    }

    @Test
    public void testForeignBindingsAndSharesAreRejected() throws Exception {
        ProcessCardsApi cards = new ProcessCardsApi(getApiClient());
        ProjectsApi projects = new ProjectsApi(getApiClient());
        TeamsApi teams = new TeamsApi(getApiClient());
        withOrg(orgName -> withProject(orgName, projectName ->
                withOrg(foreignOrg -> withProject(foreignOrg, foreignProject -> {
                    UUID foreignProjectId = projects.getProject(foreignOrg, foreignProject).getId();
                    Map<String, Object> input = new ProcessCardsApi.CreateOrUpdateProcessCardRequest()
                            .org(orgName).project(projectName).name("foreign").asMap();
                    input.put("projectId", foreignProjectId.toString());
                    assertEquals(400, assertThrows(ApiException.class, () -> cards.createOrUpdateProcessCard(input)).getCode());
                    input.remove("projectId");
                    input.put("orgId", new OrganizationsApi(getApiClient()).getOrg(foreignOrg).getId().toString());
                    assertEquals(400, assertThrows(ApiException.class, () -> cards.createOrUpdateProcessCard(input)).getCode());
                    input.remove("orgId");
                    RepositoriesApi repositories = new RepositoriesApi(getApiClient());
                    repositories.createOrUpdateRepository(foreignOrg, foreignProject,
                            new RepositoryEntry().name("foreign").url("https://example.invalid/repo.git").branch("main").disabled(true));
                    input.put("repoId", repositories.getRepository(foreignOrg, foreignProject, "foreign").getId().toString());
                    assertEquals(400, assertThrows(ApiException.class, () -> cards.createOrUpdateProcessCard(input)).getCode());
                    input.remove("repoId");
                    UUID cardId = cards.createOrUpdateProcessCard(input).getId();
                    UUID foreignTeamId = teams.createOrUpdateTeam(foreignOrg, new TeamEntry().name("foreign")).getId();
                    assertEquals(400, assertThrows(ApiException.class, () -> cards.processCardAccess(cardId,
                            new ProcessCardAccessEntry().teamIds(List.of(foreignTeamId)))).getCode());
                    assertEquals(cardId, cards.getProcessCard(cardId).getId());
                }))));
    }

    private HttpResponse<String> postStart(ApiClient client, UUID cardId, String arguments) throws Exception {
        HttpRequest request = client.requestBuilder()
                .uri(URI.create(client.getBaseUrl() + "/api/v1/processcard/" + cardId + "/start"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(arguments)).build();
        return client.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
