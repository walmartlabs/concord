package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.*;

public class ProjectIT extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = "main";
        String greeting = "Hello, _" + randomString();
        Map<String, Object> args = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---

        ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, entryPoint, args, false);

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*" + greeting + ".*", ab);
    }

    private static Git initRepo(Path initDir) throws Exception {
        return Git.init().setInitialBranch("master").setDirectory(initDir.toFile()).call();
    }

    @Test
    public void testEntryPointFromYml() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("projectEntryPoint").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        // ---

        ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, null, Collections.emptyMap(), false);

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test
    public void testWithCommitId() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();

            // commit-1
            PathUtils.deleteRecursively(tmpDir.resolve("processes"));
            src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
            PathUtils.copy(src.toPath().resolve("1"), tmpDir);
            Ref result = repo.checkout().setName("test-branch").setCreateBranch(true).call();
            assertNotNull(result);

            repo.add().addFilepattern(".").call();
            RevCommit cmt = repo.commit().setMessage("commit-1").call();
            String commitId = cmt.getId().getName();

            // commit-2
            result = repo.checkout().setName("master").call();
            PathUtils.deleteRecursively(tmpDir.resolve("processes"));
            src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
            PathUtils.copy(src.toPath().resolve("2"), tmpDir);
            assertNotNull(result);
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("commit-2").call();

            String gitUrl = tmpDir.toAbsolutePath().toString();

            // ---

            String projectName = "myProject_" + randomString();
            String username = "myUser_" + randomString();
            Set<String> permissions = Collections.emptySet();
            String repoName = "myRepo_" + randomString();
            String repoUrl = gitUrl;
            String entryPoint = "main";
            String greeting = "Hello, _" + randomString();
            Map<String, Object> args = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                    Collections.singletonMap("greeting", greeting));

            // ---
            ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, entryPoint, args, commitId, null, false);

            byte[] ab = getLog(psr.getInstanceId());
            assertLog(".*test-commit-1.*" + greeting + ".*", ab);
        }
    }

    @Test
    public void testWithTag() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        String tag = "tag_for_testing";

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();

            // commit-1
            PathUtils.deleteRecursively(tmpDir.resolve("processes"));
            src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
            PathUtils.copy(src.toPath().resolve("1"), tmpDir);
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("commit-1").call();

            repo.tag().setName(tag).call();

            // commit-2
            PathUtils.deleteRecursively(tmpDir.resolve("processes"));
            src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
            PathUtils.copy(src.toPath().resolve("2"), tmpDir);
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("commit-2").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---
        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = "main";
        String greeting = "Hello, _" + randomString();
        Map<String, Object> args = Collections.singletonMap(Constants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---
        ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, entryPoint, args, null, tag, false);

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*test-commit-1.*" + greeting + ".*", ab);
    }

    @Test
    public void testInitImport() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project-triggers").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        createProjectAndRepo(projectName, username, permissions, repoName, repoUrl, null, null);

        TriggersApi triggersApi = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> triggers = triggersApi.listTriggers("Default", projectName, repoName);
            if (hasCondition("github", "branch", "foo", triggers) &&
                    hasCondition("github", "branch", "foo2", triggers) &&
                    hasCondition("oneops", "org", "myOrg", triggers)) {
                break;
            }

            Thread.sleep(1_000);
        }
    }

    @Test
    public void testRepositoryValidation() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("repositoryValidation").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = initRepo(tmpDir)) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        // ---

        createProjectAndRepo(projectName, username, permissions, repoName, repoUrl, null, null);

        // ---

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        RepositoryValidationResponse result = repositoriesApi.validateRepository("Default", projectName, repoName);
        assertTrue(result.getOk());
    }

    @Test
    public void testRepositoryValidationForEmptyFlow() throws Exception {
        assertThrows(Exception.class, () -> {
            Path tmpDir = createTempDir();

            File src = new File(ProjectIT.class.getResource("repositoryValidationEmptyFlow").toURI());
            PathUtils.copy(src.toPath(), tmpDir);

            try (Git repo = initRepo(tmpDir)) {
                repo.add().addFilepattern(".").call();
                repo.commit().setMessage("import").call();
            }

            String gitUrl = tmpDir.toAbsolutePath().toString();

            // ---

            String projectName = "myProject_" + randomString();
            String username = "myUser_" + randomString();
            Set<String> permissions = Collections.emptySet();
            String repoName = "myRepo_" + randomString();
            String repoUrl = gitUrl;

            // ---

            createProjectAndRepo(projectName, username, permissions, repoName, repoUrl, null, null);

            // ---

            RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
            RepositoryValidationResponse resp = repositoriesApi.validateRepository("Default", projectName, repoName);
            assertTrue(resp.getOk());
        });
    }

    @Test
    public void testRepositoryValidationForEmptyForm() throws Exception {
        assertThrows(Exception.class, () -> {
            Path tmpDir = createTempDir();

            File src = new File(ProjectIT.class.getResource("repositoryValidationEmptyForm").toURI());
            PathUtils.copy(src.toPath(), tmpDir);

            try (Git repo = initRepo(tmpDir)) {
                repo.add().addFilepattern(".").call();
                repo.commit().setMessage("import").call();
            }

            String gitUrl = tmpDir.toAbsolutePath().toString();

            // ---

            String projectName = "myProject_" + randomString();
            String username = "myUser_" + randomString();
            Set<String> permissions = Collections.emptySet();
            String repoName = "myRepo_" + randomString();
            String repoUrl = gitUrl;

            // ---

            createProjectAndRepo(projectName, username, permissions, repoName, repoUrl, null, null);

            // ---

            RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
            RepositoryValidationResponse resp = repositoriesApi.validateRepository("Default", projectName, repoName);
            assertTrue(resp.getOk());
        });
    }

    @Test
    public void testDisabledRepository() throws Exception {
        assertThrows(Exception.class, () -> {
            Path tmpDir = createTempDir();

            File src = new File(ProjectIT.class.getResource("ProcessDisabledRepo").toURI());
            PathUtils.copy(src.toPath(), tmpDir);

            try (Git repo = initRepo(tmpDir)) {
                repo.add().addFilepattern(".").call();
                repo.commit().setMessage("import").call();
            }

            String gitUrl = tmpDir.toAbsolutePath().toString();

            // ---
            String orgName = "Default";
            String projectName = "myProject_" + randomString();
            String repoName = "myRepo_" + randomString();
            String repoUrl = gitUrl;

            ProjectsApi projectsApi = new ProjectsApi(getApiClient());
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                            .name(repoName).url(repoUrl)
                            .disabled(true))));

            // ---
            Map<String, Object> input = new HashMap<>();
            input.put("org", orgName);
            input.put("project", projectName);
            input.put("repo", repoName);

            start(input);
        });
    }

    @Test
    public void testBulkAccessUpdate() throws Exception {
        String orgName = "org_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // ---

        String projectName = "project_" + randomString();

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName));

        // ---

        String teamName = "team_" + randomString();
        TeamsApi teamsApi = new TeamsApi(getApiClient());
        CreateTeamResponse teamResp = teamsApi.createOrUpdateTeam(orgName, new TeamEntry()
                .name(teamName));

        // --- Typical one-or-more teams bulk access update

        List<ResourceAccessEntry> teams = new ArrayList<>(1);
        teams.add(new ResourceAccessEntry()
                .orgName(orgName)
                .teamId(teamResp.getId())
                .teamName(teamName)
                .level(ResourceAccessEntry.LevelEnum.OWNER));
        GenericOperationResult addTeamsResult = projectsApi.updateProjectAccessLevelBulk(orgName, projectName, teams);
        assertNotNull(addTeamsResult);
        assertTrue(addTeamsResult.getOk());

        List<ResourceAccessEntry> currentTeams = projectsApi.getProjectAccessLevel(orgName, projectName);
        assertNotNull(currentTeams);
        assertEquals(1, currentTeams.size());

        // --- Empty teams list clears all

        GenericOperationResult clearTeamsResult = projectsApi.updateProjectAccessLevelBulk(orgName, projectName, Collections.emptyList());
        assertNotNull(clearTeamsResult);
        assertTrue(clearTeamsResult.getOk());

        currentTeams = projectsApi.getProjectAccessLevel(orgName, projectName);
        assertNotNull(currentTeams);
        assertEquals(0, currentTeams.size());

        // --- Null list not allowed, throws error

        try {
            projectsApi.updateProjectAccessLevelBulk(orgName, projectName, null);
        } catch (ApiException expected) {
            assertEquals(400, expected.getCode());
            assertTrue(expected.getResponseBody().contains("List of teams is null"));
        } catch (Exception e) {
            fail("Expected ApiException. Got " + e.getClass().toString());
        }

        // ---

        teamsApi.deleteTeam(orgName, teamName);
        projectsApi.deleteProject(orgName, projectName);
        orgApi.deleteOrg(orgName, "yes");
    }

    private static boolean hasCondition(String src, String k, Object v, Collection<TriggerEntry> entries) {
        for (TriggerEntry e : entries) {
            Map<String, Object> c = e.getConditions();
            if (c == null || c.isEmpty()) {
                continue;
            }

            if (!src.equals(e.getEventSource())) {
                continue;
            }

            if (v.equals(c.get(k))) {
                return true;
            }
        }
        return false;
    }

    protected void createProjectAndRepo(String projectName,
                                        String username,
                                        Set<String> permissions,
                                        String repoName,
                                        String repoUrl,
                                        String commitId,
                                        String tag) throws Exception {

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(cur.getOk());

        UUID userId = cur.getId();

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.createUserApiKey(new CreateApiKeyRequest()
                .userId(userId)
                .userType(CreateApiKeyRequest.UserTypeEnum.LOCAL));
        assertTrue(cakr.getOk());

        String apiKey = cakr.getKey();

        // ---

        setApiKey(apiKey);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdateProject("Default", new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName,
                        new RepositoryEntry()
                                .name(repoName)
                                .url(repoUrl)
                                .branch(tag != null ? tag : "master")
                                .commitId(commitId))));
        assertTrue(cpr.getOk());
    }

    protected ProcessEntry doTest(String projectName,
                                  String username, Set<String> permissions,
                                  String repoName, String repoUrl,
                                  String entryPoint, Map<String, Object> args,
                                  boolean sync) throws Exception {
        return doTest(projectName, username, permissions, repoName, repoUrl,
                entryPoint, args, null, null, sync);
    }

    protected ProcessEntry doTest(String projectName,
                                  String username, Set<String> permissions,
                                  String repoName, String repoUrl,
                                  String entryPoint, Map<String, Object> args,
                                  String commitId, String tag,
                                  boolean sync) throws Exception {

        // ---

        createProjectAndRepo(projectName, username, permissions, repoName, repoUrl, commitId, tag);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        Map<String, Object> input = new HashMap<>();
        if (projectName != null) {
            input.put("org", "Default");
            input.put("project", projectName);
        }
        if (repoName != null) {
            input.put("repo", repoName);
        }
        if (entryPoint != null) {
            input.put("entryPoint", entryPoint);
        }
        input.put("request", args);
        input.put("sync", sync);
        StartProcessResponse spr = start(input);
        assertTrue(spr.getOk());

        UUID instanceId = spr.getInstanceId();

        // ---

        ProcessEntry psr = waitForCompletion(getApiClient(), instanceId);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, psr.getStatus());

        // ---

        return psr;
    }
}
