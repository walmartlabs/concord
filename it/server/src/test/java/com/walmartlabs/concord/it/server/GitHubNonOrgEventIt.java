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
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitHubUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitHubNonOrgEventIt extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(GitHubNonOrgEventIt.class.getResource("githubNonRepoEvent").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call();
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

    private void githubEvent(String eventFile, String repoName, String eventName) throws Exception {
        String event = new String(Files.readAllBytes(Paths.get(GitHubNonOrgEventIt.class.getResource(eventFile).toURI())));
        if (repoName != null) {
            event = event.replace("org-repo", repoName);
        }

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(event));

        GitHubEventsApi gitHubEvents = new GitHubEventsApi(client);

        String result = gitHubEvents.onEvent(event, "abc", eventName);
        assertEquals("ok", result);
    }
}
