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

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertTrue;

public class ProjectIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void test() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

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

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*" + greeting + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testEntryPointFromYml() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("projectEntryPoint").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String username = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        // ---

        ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, null, Collections.emptyMap(), false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWithCommitId() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        // commit-1
        IOUtils.deleteRecursively(tmpDir.resolve("processes"));
        src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
        IOUtils.copy(src.toPath().resolve("1"), tmpDir);
        repo.add().addFilepattern(".").call();
        RevCommit cmt = repo.commit().setMessage("commit-1").call();
        String commitId = cmt.getId().getName();

        // commit-2
        IOUtils.deleteRecursively(tmpDir.resolve("processes"));
        src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
        IOUtils.copy(src.toPath().resolve("2"), tmpDir);
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

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*test-commit-1.*" + greeting + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testWithTag() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        // commit-1
        IOUtils.deleteRecursively(tmpDir.resolve("processes"));
        src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
        IOUtils.copy(src.toPath().resolve("1"), tmpDir);
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("commit-1").call();

        String tag = "tag_for_testing";
        repo.tag().setName(tag).call();

        // commit-2
        IOUtils.deleteRecursively(tmpDir.resolve("processes"));
        src = new File(ProjectIT.class.getResource("project-commit-id").toURI());
        IOUtils.copy(src.toPath().resolve("2"), tmpDir);
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
        ProcessEntry psr = doTest(projectName, username, permissions, repoName, repoUrl, entryPoint, args, null, tag, false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*test-commit-1.*" + greeting + ".*", ab);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testInitImport() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project-triggers").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

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
            List<TriggerEntry> triggers = triggersApi.list("Default", projectName, repoName);
            if (hasCondition("github", "repository", "abc", triggers) &&
                    hasCondition("github", "repository", "abc2", triggers) &&
                    hasCondition("oneops", "org", "myOrg", triggers)) {
                break;
            }

            Thread.sleep(1_000);
        }
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testRepositoryValidation() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("repositoryValidation").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

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
        assertTrue(result.isOk());
    }

    @Test(expected = Exception.class)
    public void testRepositoryValidationForEmptyFlow() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("repositoryValidationEmptyFlow").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

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
        repositoriesApi.validateRepository("Default", projectName, repoName);
    }

    @Test(expected = Exception.class)
    public void testRepositoryValidationForEmptyForm() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("repositoryValidationEmptyForm").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

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
        repositoriesApi.validateRepository("Default", projectName, repoName);
    }

    @Test(expected = Exception.class)
    public void testDisabledRepository() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("ProcessDisabledRepo").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---
        String orgName = "Default";
        String projectName = "myProject_" + randomString();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setName(repoName).setUrl(repoUrl)
                        .setDisabled(true))));

        // ---
        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);

        start(input);
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
                                        String repoName, String repoUrl,
                                        String commitId, String tag) throws Exception {

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdate(new CreateUserRequest()
                .setUsername(username)
                .setType(CreateUserRequest.TypeEnum.LOCAL));
        assertTrue(cur.isOk());

        UUID userId = cur.getId();

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest()
                .setUserId(userId)
                .setUserType(CreateApiKeyRequest.UserTypeEnum.LOCAL));
        assertTrue(cakr.isOk());

        String apiKey = cakr.getKey();

        // ---

        setApiKey(apiKey);

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdate("Default", new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName,
                        new RepositoryEntry()
                                .setName(repoName)
                                .setUrl(repoUrl)
                                .setBranch(tag)
                                .setCommitId(commitId))));
        assertTrue(cpr.isOk());
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
        assertTrue(spr.isOk());

        UUID instanceId = spr.getInstanceId();

        // ---

        ProcessEntry psr = waitForCompletion(processApi, instanceId);
        assertTrue(psr.getStatus() == ProcessEntry.StatusEnum.FINISHED);

        // ---

        return psr;
    }
}
