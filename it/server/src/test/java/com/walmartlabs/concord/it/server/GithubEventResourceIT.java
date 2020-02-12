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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitHubUtils;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.common.IOUtils.grep;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GithubEventResourceIT extends AbstractServerIT {

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void pushUnknownRepo() throws Exception {
        String processTag = "tag_" + randomString();
        Path tmpDir = createTempDir();

        File src = new File(GithubEventResourceIT.class.getResource("githubEvent").toURI());
        IOUtils.copy(src.toPath(), tmpDir);
        updateTag(tmpDir.resolve("concord.yml"), processTag);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "Default";
        String projectName = "test_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = gitUrl;

        // ---

        UUID projectId = createProjectAndRepo(orgName, projectName, repoName, repoUrl);

        // ---

        String externalRepoName = "ext_" + randomString();
        githubEvent("githubEvent/push_unknown_repo.json", externalRepoName);

        List<ProcessEntry> processes = waitProcesses(projectId, processTag, 2);
        removeProcessWithLog(processes, ".*onOnlyUnknownRepo.*" + externalRepoName + ".*", 1);
        removeProcessWithLog(processes, ".*onAllRepo.*" + externalRepoName + ".*", 1);
        assertTrue(processes.toString(), processes.isEmpty());

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.delete(orgName, projectName);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void pushWithPayload() throws Exception {
        String orgName = "Default";
        String projectName = "test_" + randomString();
        String repoName = "repo_" + randomString();

        String processTag = "tag_" + randomString();
        Path tmpDir = createTempDir();

        File src = new File(GithubEventResourceIT.class.getResource("githubEventWithPayload").toURI());
        IOUtils.copy(src.toPath(), tmpDir);
        Path concordFile = tmpDir.resolve("concord.yml");
        updateTag(concordFile, processTag);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        UUID projectId = createProjectAndRepo(orgName, projectName, repoName, gitUrl);

        // ---

        githubEvent("githubEventWithPayload/push_request.json", tmpDir.getFileName().toString());

        List<ProcessEntry> processes = waitProcesses(projectId, processTag, 1);
        removeProcessWithLog(processes, ".*githubEventWithPayload onPush.*", 1);
        assertTrue(processes.toString(), processes.isEmpty());

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.delete(orgName, projectName);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void nonOrgEvent() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource("githubNonRepoEvent").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setDirectory(tmpDir.toFile()).call();
        repo.add().addFilepattern(".").call();
        repo.commit().setMessage("import").call();

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();
        String projectName = "test_" + randomString();
        String repoName = "repo_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdate(new OrganizationEntry()
                .setName(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(gitUrl))));

        // ---

        TriggersApi triggersApi = new TriggersApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<TriggerEntry> triggers = triggersApi.list(orgName, projectName, repoName);
            if (!triggers.isEmpty()) {
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        githubEvent("githubNonRepoEvent/event.json", null, "team");

        List<ProcessEntry> processes;

        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());
        while (true) {
            processes = processV2Api.list(null, orgName, null, projectName, null, null, null, null, null, ProcessEntry.StatusEnum.FINISHED.getValue(), null, null, null, null, null);
            if (processes.size() > 0) {
                break;
            }

            Thread.sleep(1000);
        }

        assertEquals(1, processes.size());

        // ---

        ProcessEntry pe = processes.get(0);

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*EVENT:.*added_to_repository.*", ab);
    }

    private void removeProcessWithLog(List<ProcessEntry> processes, String log, int expectedCount) throws Exception {
        Iterator<ProcessEntry> it = processes.iterator();
        while (it.hasNext()) {
            ProcessEntry psr = it.next();
            byte[] ab = getLog(psr.getLogFileName());
            int count = grep(log, ab).size();
            if (count == expectedCount) {
                it.remove();
            }
        }
    }

    private List<ProcessEntry> waitProcesses(UUID projectId, String processTag, int expectedCount) throws ApiException, InterruptedException {
        ProcessApi processApi = new ProcessApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<ProcessEntry> process = processApi.list(null, null, projectId, null, null, Collections.singletonList(processTag), ProcessEntry.StatusEnum.FINISHED.getValue(), null, null, expectedCount + 1, 0);
            if (process.size() == expectedCount) {
                return process;
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException("oops");
    }

    private void updateTag(Path concord, String processTag) throws IOException {
        replace(concord, "{{tag}}", processTag);
    }

    private void replace(Path concord, String what, String newValue) throws IOException {
        List<String> fileContent = Files.readAllLines(concord, StandardCharsets.UTF_8).stream()
                .map(l -> l.replaceAll(Pattern.quote(what), newValue))
                .collect(Collectors.toList());

        Files.write(concord, fileContent, StandardCharsets.UTF_8);
    }

    private void githubEvent(String eventFile, String repoName) throws Exception {
        githubEvent(eventFile, repoName, "push");
    }

    private void githubEvent(String eventFile, String repoName, String eventName) throws Exception {
        String event = new String(Files.readAllBytes(Paths.get(GithubEventResourceIT.class.getResource(eventFile).toURI())));
        if (repoName != null) {
            event = event.replace("org-repo", repoName);
        }

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(event));

        GitHubEventsApi gitHubEvents = new GitHubEventsApi(client);

        String result = gitHubEvents.onEvent(event, "abc", eventName);
        assertEquals("ok", result);
    }

    private UUID createProjectAndRepo(String orgName, String projectName, String repoName, String repoUrl) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse cpr = projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName,
                        new RepositoryEntry()
                                .setName(repoName)
                                .setUrl(repoUrl))));
        assertTrue(cpr.isOk());

        // wait triggers created
        TriggersApi triggersApi = new TriggersApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<TriggerEntry> triggers = triggersApi.list(orgName, projectName, repoName);
            if (!triggers.isEmpty()) {
                break;
            }

            Thread.sleep(1000);
        }

        return cpr.getId();
    }
}
