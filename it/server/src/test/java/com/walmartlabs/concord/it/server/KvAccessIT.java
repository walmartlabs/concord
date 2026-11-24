package com.walmartlabs.concord.it.server;

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

import com.walmartlabs.concord.client2.*;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.waitForStatus;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Access control of the process KV API ({@code /api/v1/process/{id}/kv/{key}}).
 */
public class KvAccessIT extends AbstractServerIT {

    @Test
    public void testPrivateProjectAccessLevels() throws Exception {
        Fixture f = new Fixture(ProjectEntry.VisibilityEnum.PRIVATE);

        User reader = f.addUser("reader", ResourceAccessEntry.LevelEnum.READER);
        User writer = f.addUser("writer", ResourceAccessEntry.LevelEnum.WRITER);
        User outsider = f.addUser("outsider", null);

        String key = "key_" + randomString();
        String longKey = "long_" + randomString();

        // admin can do anything

        ProcessKvStoreApi admin = new ProcessKvStoreApi(getApiClient());
        admin.putKvString(f.instanceId, key, "admin");
        admin.putKvLong(f.instanceId, longKey, 10L);
        assertEquals("admin", admin.getKvString(f.instanceId, key));
        assertEquals(10L, admin.getKvLong(f.instanceId, longKey));

        // READER: read-only

        ProcessKvStoreApi readerApi = kvApi(reader);
        assertEquals("admin", readerApi.getKvString(f.instanceId, key));
        assertEquals(10L, readerApi.getKvLong(f.instanceId, longKey));

        assertForbidden(() -> readerApi.putKvString(f.instanceId, key, "reader"));
        assertForbidden(() -> readerApi.putKvLong(f.instanceId, longKey, 20L));
        assertForbidden(() -> readerApi.incKvLong(f.instanceId, longKey));
        assertForbidden(() -> readerApi.deleteKv(f.instanceId, key));

        // nothing was changed by the reader

        assertEquals("admin", admin.getKvString(f.instanceId, key));
        assertEquals(10L, admin.getKvLong(f.instanceId, longKey));

        // WRITER: read and write

        ProcessKvStoreApi writerApi = kvApi(writer);
        writerApi.putKvString(f.instanceId, key, "writer");
        assertEquals("writer", writerApi.getKvString(f.instanceId, key));

        writerApi.putKvLong(f.instanceId, longKey, 100L);
        assertEquals(101L, writerApi.incKvLong(f.instanceId, longKey));
        assertEquals(101L, writerApi.getKvLong(f.instanceId, longKey));

        writerApi.deleteKv(f.instanceId, key);
        assertNull(writerApi.getKvString(f.instanceId, key));

        // no access at all

        String otherKey = "key_" + randomString();
        admin.putKvString(f.instanceId, otherKey, "secret");

        ProcessKvStoreApi outsiderApi = kvApi(outsider);
        assertForbidden(() -> outsiderApi.getKvString(f.instanceId, otherKey));
        assertForbidden(() -> outsiderApi.getKvLong(f.instanceId, longKey));
        assertForbidden(() -> outsiderApi.putKvString(f.instanceId, otherKey, "outsider"));
        assertForbidden(() -> outsiderApi.putKvLong(f.instanceId, longKey, 1L));
        assertForbidden(() -> outsiderApi.incKvLong(f.instanceId, longKey));
        assertForbidden(() -> outsiderApi.deleteKv(f.instanceId, otherKey));

        assertEquals("secret", admin.getKvString(f.instanceId, otherKey));
    }

    @Test
    public void testPublicProjectOrgMemberCanOnlyRead() throws Exception {
        Fixture f = new Fixture(ProjectEntry.VisibilityEnum.PUBLIC);

        // a member of the org (via an unrelated team) without any explicit access to the project
        User member = f.addUser("member", null);
        f.addToOrg(member);

        // not a member of the org
        User outsider = f.addUser("outsider", null);

        String key = "key_" + randomString();
        new ProcessKvStoreApi(getApiClient()).putKvString(f.instanceId, key, "value");

        ProcessKvStoreApi memberApi = kvApi(member);
        assertEquals("value", memberApi.getKvString(f.instanceId, key));
        assertForbidden(() -> memberApi.putKvString(f.instanceId, key, "member"));
        assertForbidden(() -> memberApi.deleteKv(f.instanceId, key));

        // PUBLIC visibility doesn't open the KV store to users outside of the org
        ProcessKvStoreApi outsiderApi = kvApi(outsider);
        assertForbidden(() -> outsiderApi.getKvString(f.instanceId, key));
        assertForbidden(() -> outsiderApi.putKvString(f.instanceId, key, "outsider"));

        assertEquals("value", new ProcessKvStoreApi(getApiClient()).getKvString(f.instanceId, key));
    }

    @Test
    public void testUnknownProcess() throws Exception {
        UUID unknown = UUID.randomUUID();

        ApiException e = assertThrows(ApiException.class,
                () -> new ProcessKvStoreApi(getApiClient()).getKvString(unknown, "key_" + randomString()));
        assertEquals(404, e.getCode());

        e = assertThrows(ApiException.class,
                () -> new ProcessKvStoreApi(getApiClient()).putKvString(unknown, "key_" + randomString(), "x"));
        assertEquals(404, e.getCode());
    }

    @Test
    public void testProcessWithoutProject() throws Exception {
        // a process without a project uses the shared KV namespace
        StartProcessResponse spr = start(archive(KvAccessIT.class.getResource("kvAccess").toURI()),
                "arguments.kvKey", "key_" + randomString());
        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);

        String key = "key_" + randomString();

        // admins are allowed
        ProcessKvStoreApi admin = new ProcessKvStoreApi(getApiClient());
        admin.putKvString(spr.getInstanceId(), key, "value");
        assertEquals("value", admin.getKvString(spr.getInstanceId(), key));

        // regular users are not
        User user = new Fixture(ProjectEntry.VisibilityEnum.PRIVATE).addUser("user", null);
        ProcessKvStoreApi userApi = kvApi(user);
        assertForbidden(() -> userApi.getKvString(spr.getInstanceId(), key));
        assertForbidden(() -> userApi.putKvString(spr.getInstanceId(), key, "user"));
        assertForbidden(() -> userApi.deleteKv(spr.getInstanceId(), key));

        assertEquals("value", admin.getKvString(spr.getInstanceId(), key));
    }

    /**
     * The {@code kv} task authenticates with the process' own session key. It
     * must keep working even if the user who started the process only has the
     * READER access level to the project.
     */
    @Test
    public void testKvTaskWithOwnSession() throws Exception {
        Fixture f = new Fixture(ProjectEntry.VisibilityEnum.PRIVATE);
        User reader = f.addUser("reader", ResourceAccessEntry.LevelEnum.READER);

        String key = "key_" + randomString();

        setApiKey(reader.apiKey);
        StartProcessResponse spr;
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("archive", archive(KvAccessIT.class.getResource("kvAccess").toURI()));
            input.put("org", f.orgName);
            input.put("project", f.projectName);
            input.put("arguments.kvKey", key);
            spr = start(input);
        } finally {
            resetApiKey();
        }

        ProcessEntry pe = waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
        assertNotNull(pe);

        assertEquals("fromProcess", new ProcessKvStoreApi(getApiClient()).getKvString(f.instanceId, key));

        // ...but the same user can't write through the API
        ProcessKvStoreApi readerApi = kvApi(reader);
        assertEquals("fromProcess", readerApi.getKvString(f.instanceId, key));
        assertForbidden(() -> readerApi.putKvString(f.instanceId, key, "reader"));
    }

    // ---

    private StartProcessResponse start(byte[] archive, String argName, Object argValue) throws ApiException {
        Map<String, Object> input = new HashMap<>();
        input.put("archive", archive);
        input.put(argName, argValue);
        return start(input);
    }

    private ProcessKvStoreApi kvApi(User user) {
        return new ProcessKvStoreApi(getApiClientForKey(user.apiKey));
    }

    private static void assertForbidden(ThrowingRunnable r) {
        ApiException e = assertThrows(ApiException.class, r::run);
        assertEquals(403, e.getCode(), e.getResponseBody());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws Exception;
    }

    private static final class User {

        private final String name;
        private final String apiKey;

        private User(String name, String apiKey) {
            this.name = name;
            this.apiKey = apiKey;
        }
    }

    /**
     * An org with a project and a finished process in that project.
     */
    private final class Fixture {

        private final String orgName = "org_" + randomString();
        private final String projectName = "project_" + randomString();
        private final String teamName = "team_" + randomString();
        private final UUID instanceId;

        private Fixture(ProjectEntry.VisibilityEnum visibility) throws Exception {
            new OrganizationsApi(getApiClient()).createOrUpdateOrg(new OrganizationEntry().name(orgName));

            new ProjectsApi(getApiClient()).createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .visibility(visibility)
                    .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

            Map<String, Object> input = new HashMap<>();
            input.put("archive", archive(KvAccessIT.class.getResource("kvAccess").toURI()));
            input.put("org", orgName);
            input.put("project", projectName);
            input.put("arguments.kvKey", "key_" + randomString());
            StartProcessResponse spr = start(input);
            waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FINISHED);
            this.instanceId = spr.getInstanceId();
        }

        /**
         * Creates a user. If the {@code level} is not {@code null}, the user is added
         * to a new team that is granted the specified access level to the project.
         */
        private User addUser(String prefix, ResourceAccessEntry.LevelEnum level) throws Exception {
            String username = prefix + "_" + randomString();

            new UsersApi(getApiClient()).createOrUpdateUser(new CreateUserRequest()
                    .username(username)
                    .type(CreateUserRequest.TypeEnum.LOCAL));

            CreateApiKeyResponse key = new ApiKeysApi(getApiClient())
                    .createUserApiKey(new CreateApiKeyRequest().username(username));

            User u = new User(username, key.getKey());

            if (level != null) {
                String team = "team_" + randomString();

                TeamsApi teamsApi = new TeamsApi(getApiClient());
                CreateTeamResponse ctr = teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(team));
                teamsApi.addUsersToTeam(orgName, team, false, Collections.singletonList(new TeamUserEntry()
                        .username(username)
                        .role(TeamUserEntry.RoleEnum.MEMBER)));

                new ProjectsApi(getApiClient()).updateProjectAccessLevel(orgName, projectName, new ResourceAccessEntry()
                        .teamId(ctr.getId())
                        .orgName(orgName)
                        .teamName(team)
                        .level(level));
            }

            return u;
        }

        /**
         * Adds the user to a team of the org that has no access to the project.
         */
        private void addToOrg(User user) throws Exception {
            TeamsApi teamsApi = new TeamsApi(getApiClient());
            teamsApi.createOrUpdateTeam(orgName, new TeamEntry().name(teamName));
            teamsApi.addUsersToTeam(orgName, teamName, false, Collections.singletonList(new TeamUserEntry()
                    .username(user.name)
                    .role(TeamUserEntry.RoleEnum.MEMBER)));
        }
    }
}
