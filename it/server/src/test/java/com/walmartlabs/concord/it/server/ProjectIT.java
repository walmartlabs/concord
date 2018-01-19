package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.googlecode.junittoolbox.ParallelRunner;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.org.project.ProjectEntry;
import com.walmartlabs.concord.server.api.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerEntry;
import com.walmartlabs.concord.server.api.org.trigger.TriggerResource;
import com.walmartlabs.concord.server.api.process.ProcessEntry;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.ProcessStatus;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import com.walmartlabs.concord.server.api.project.CreateProjectResponse;
import com.walmartlabs.concord.server.api.project.ProjectResource;
import com.walmartlabs.concord.server.api.security.apikey.ApiKeyResource;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyRequest;
import com.walmartlabs.concord.server.api.security.apikey.CreateApiKeyResponse;
import com.walmartlabs.concord.server.api.user.CreateUserRequest;
import com.walmartlabs.concord.server.api.user.CreateUserResponse;
import com.walmartlabs.concord.server.api.user.UserResource;
import com.walmartlabs.concord.server.org.OrganizationManager;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.Assert.assertTrue;

@RunWith(ParallelRunner.class)
public class ProjectIT extends AbstractServerIT {

    @Test(timeout = 30000)
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
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";
        String greeting = "Hello, _" + randomString();
        Map<String, Object> args = Collections.singletonMap(InternalConstants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---

        ProcessEntry psr = doTest(projectName, userName, permissions, repoName, repoUrl, entryPoint, args, false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*" + greeting + ".*", ab);
    }

    @Test(timeout = 30000)
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
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName;

        // ---

        ProcessEntry psr = doTest(projectName, userName, permissions, repoName, repoUrl, entryPoint, Collections.emptyMap(), false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*Hello, Concord.*", ab);
    }

    @Test(timeout = 30000)
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
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";
        String greeting = "Hello, _" + randomString();
        Map<String, Object> args = Collections.singletonMap(InternalConstants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---
        ProcessEntry psr = doTest(projectName, userName, permissions, repoName, repoUrl, entryPoint, args, commitId, null, false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*test-commit-1.*" + greeting + ".*", ab);
    }


    @Test
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

        System.out.println(">>>" + gitUrl);

        // ---
        String projectName = "myProject_" + randomString();
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";
        String greeting = "Hello, _" + randomString();
        Map<String, Object> args = Collections.singletonMap(InternalConstants.Request.ARGUMENTS_KEY,
                Collections.singletonMap("greeting", greeting));

        // ---
        ProcessEntry psr = doTest(projectName, userName, permissions, repoName, repoUrl, entryPoint, args, null, tag, false);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*test-commit-1.*" + greeting + ".*", ab);
    }

    @Test(timeout = 30000)
    public void testSync() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(ProjectIT.class.getResource("project-sync").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String projectName = "myProject_" + randomString();
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;
        String entryPoint = projectName + ":" + repoName + ":main";

        Map<String, Object> args = Collections.singletonMap(InternalConstants.Request.ARGUMENTS_KEY,
                ImmutableMap.of(
                        "myForm1", ImmutableMap.of(
                                "x", 100123, "firstName", "Boo"),
                        "myForm2", ImmutableMap.of(
                                "lastName", "Zoo",
                                "age", 1200,
                                "color", "red")
                ));

        // ---

        ProcessEntry psr = doTest(projectName, userName, permissions, repoName, repoUrl, entryPoint, args, true);

        byte[] ab = getLog(psr.getLogFileName());
        assertLog(".*110123.*", ab);
        assertLog(".*Boo Zoo.*", ab);
        assertLog(".*101200.*", ab);
        assertLog(".*120123.*", ab);
        assertLog(".*red.*", ab);

        assertTrue(psr.getStatus() == ProcessStatus.FINISHED);
    }

    @Test(timeout = 30000)
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
        String userName = "myUser_" + randomString();
        Set<String> permissions = Collections.emptySet();
        String repoName = "myRepo_" + randomString();
        String repoUrl = gitUrl;

        createProjectAndRepo(projectName, userName, permissions, repoName, repoUrl, null, null);

        TriggerResource triggerResource = proxy(TriggerResource.class);
        while (true) {
            List<TriggerEntry> triggers = triggerResource.list(OrganizationManager.DEFAULT_ORG_NAME, projectName, repoName);
            if (hasCondition("github", "repository", "abc", triggers) &&
                    hasCondition("github", "repository", "abc2", triggers) &&
                    hasCondition("oneops", "org", "myOrg", triggers)) {
                break;
            }

            Thread.sleep(1_000);
        }
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
                                        String userName,
                                        Set<String> permissions,
                                        String repoName, String repoUrl,
                                        String commitId, String tag) {

        UserResource userResource = proxy(UserResource.class);
        CreateUserResponse cur = userResource.createOrUpdate(new CreateUserRequest(userName, permissions, false));
        assertTrue(cur.isOk());

        UUID userId = cur.getId();

        ApiKeyResource apiKeyResource = proxy(ApiKeyResource.class);
        CreateApiKeyResponse cakr = apiKeyResource.create(new CreateApiKeyRequest(userId, null));
        assertTrue(cakr.isOk());

        String apiKey = cakr.getKey();

        // ---

        setApiKey(apiKey);

        ProjectResource projectResource = proxy(ProjectResource.class);
        CreateProjectResponse cpr = projectResource.createOrUpdate(new ProjectEntry(projectName,
                Collections.singletonMap(repoName,
                        new RepositoryEntry(null, null, repoName, repoUrl, tag, commitId, null, null, null))));
        assertTrue(cpr.isOk());
    }

    protected ProcessEntry doTest(String projectName,
                                  String userName, Set<String> permissions,
                                  String repoName, String repoUrl,
                                  String entryPoint, Map<String, Object> args,
                                  boolean sync) throws InterruptedException, IOException {
        return doTest(projectName, userName, permissions, repoName, repoUrl,
                entryPoint, args, null, null, sync);
    }

    protected ProcessEntry doTest(String projectName,
                                  String userName, Set<String> permissions,
                                  String repoName, String repoUrl,
                                  String entryPoint, Map<String, Object> args,
                                  String commitId, String tag,
                                  boolean sync) throws InterruptedException, IOException {

        // ---

        createProjectAndRepo(projectName, userName, permissions, repoName, repoUrl, commitId, tag);

        // ---

        ProcessResource processResource = proxy(ProcessResource.class);
        StartProcessResponse spr = processResource.start(entryPoint, args, null, sync, null);
        assertTrue(spr.isOk());

        UUID instanceId = spr.getInstanceId();

        // ---

        ProcessEntry psr = waitForCompletion(processResource, instanceId);
        assertTrue(psr.getStatus() == ProcessStatus.FINISHED);

        // ---

        return psr;
    }
}
