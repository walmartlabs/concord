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
import com.walmartlabs.concord.client2.ProcessListFilter;
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
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitHubNonOrgEventIt extends AbstractServerIT {

    @Test
    public void test() throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(GitHubNonOrgEventIt.class.getResource("githubNonRepoEvent").toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();
        String projectName = "test_" + randomString();
        String repoName = "repo_" + randomString();

        OrganizationsApi organizationsApi = new OrganizationsApi(getApiClient());
        organizationsApi.createOrUpdateOrg(new OrganizationEntry()
                .name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl))));

        // ---

        TriggersApi triggersApi = new TriggersApi(getApiClient());

        while (!Thread.currentThread().isInterrupted()) {
            List<TriggerEntry> triggers = triggersApi.listTriggers(orgName, projectName, repoName);
            if (!triggers.isEmpty()) {
                break;
            }

            Thread.sleep(1000);
        }

        // ---

        githubEvent("githubNonRepoEvent/event.json", null, "team");

        List<ProcessEntry> processes;

        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .status(ProcessEntry.StatusEnum.FINISHED)
                .build();

        while (true) {
            processes = processV2Api.listProcesses(filter);
            if (!processes.isEmpty()) {
                break;
            }

            Thread.sleep(1000);
        }

        assertEquals(1, processes.size());

        // ---

        ProcessEntry pe = processes.get(0);

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*EVENT:.*added_to_repository.*", ab);
    }

    @SuppressWarnings("unchecked")
    private void githubEvent(String eventFile, String repoName, String eventName) throws Exception {
        String eventStr = new String(Files.readAllBytes(Paths.get(GitHubNonOrgEventIt.class.getResource(eventFile).toURI())));
        if (repoName != null) {
            eventStr = eventStr.replace("org-repo", repoName);
        }

        Map<String, Object> event = getApiClient().getObjectMapper().readValue(eventStr, Map.class);
        eventStr = getApiClient().getObjectMapper().writeValueAsString(event);

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(eventStr));

        GitHubEventsApi gitHubEvents = new GitHubEventsApi(client);

        String result = gitHubEvents.onEvent(null, "abc", eventName, event);
        assertEquals("ok", result);
    }
}
