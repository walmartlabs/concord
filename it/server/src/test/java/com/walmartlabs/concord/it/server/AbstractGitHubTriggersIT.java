package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitHubUtils;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.ITUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.eclipse.jgit.api.Git;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class AbstractGitHubTriggersIT extends AbstractServerIT {

    protected static String toRepoName(Path p) {
        return p.toAbsolutePath().toString();
    }

    protected Path initRepo(String resource) throws Exception {
        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src);
    }

    protected Path initRepo(String resource, String leaf) throws Exception {
        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src, "init", null, leaf);
    }

    protected Path initProjectAndRepo(String orgName, String projectName, String repoName, String repoBranch, Path bareRepo) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        RepositoryEntry repo = new RepositoryEntry()
                .branch(repoBranch != null ? repoBranch : "master")
                .url("file://" + bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .repositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    protected String createNewBranch(Path bareRepo, String branch, String resource) throws Exception {
        Path src = Paths.get(AbstractGitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createNewBranch(bareRepo, branch, src);
    }

    protected void updateConcordYml(Path bareRepo, Map<String, String> values) throws Exception {
        Path dir = IOUtils.createTempDir("git");

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        Path concordYml = dir.resolve("concord.yml");
        String s = new String(Files.readAllBytes(concordYml));
        for (Map.Entry<String, String> e : values.entrySet()) {
            s = s.replaceAll(e.getKey(), e.getValue());
        }
        Files.write(concordYml, s.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage("updating concord.yml")
                .call();

        git.push()
                .call();
    }

    protected void refreshRepo(String orgName, String projectName, String repoName) throws Exception {
        RepositoriesApi repoApi = new RepositoriesApi(getApiClient());
        repoApi.refreshRepository(orgName, projectName, repoName, true);
    }

    protected ProcessEntry waitForAProcess(String orgName, String projectName, String initiator) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .initiator(initiator)
                .build();

        while (!Thread.currentThread().isInterrupted()) {
            List<ProcessEntry> l = processApi.listProcesses(filter);
            if (l.size() == 1 && isFinished(l.get(0).getStatus())) {
                return l.get(0);
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException("Process wait interrupted");
    }

    protected int waitForProcessesToFinish() throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        while (true) {
            List<ProcessEntry> l = processApi.listProcesses(ProcessListFilter.builder().build());

            boolean allDone = true;
            for (ProcessEntry e : l) {
                if (e.getStatus() == StatusEnum.NEW
                        || e.getStatus() == StatusEnum.PREPARING
                        || e.getStatus() == StatusEnum.ENQUEUED
                        || e.getStatus() == StatusEnum.STARTING
                        || e.getStatus() == StatusEnum.RUNNING) {

                    allDone = false;
                    break;
                }
            }

            if (allDone) {
                return l.size();
            }

            Thread.sleep(1000);
        }
    }

    protected void expectNoProceses(String orgName, String projectName, OffsetDateTime afterCreatedAt) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .afterCreatedAt(afterCreatedAt)
                .build();

        List<ProcessEntry> l = processApi.listProcesses(filter);
        assertEquals(0, l.size());
    }

    protected String sendEvent(String resource, String event, String... params) throws Exception {
        return sendEvent(resource, event, Collections.emptyMap(), params);
    }

    @SuppressWarnings("unchecked")
    protected String sendEvent(String resource, String event, Map<String, String> queryParams, String... params) throws Exception {
        String payload = resourceToString(resource);
        if (params != null) {
            for (int i = 0; i < params.length; i += 2) {
                String k = params[i];
                String v = params[i + 1];
                payload = payload.replaceAll(k, v);
            }
        }

        ApiClient client = getApiClient();
        Map<String, Object> payloadMap = client.getObjectMapper().readValue(payload, Map.class);
        payload = client.getObjectMapper().writeValueAsString(payloadMap);

        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(payload));

        GitHubEventsApi eventsApi = new GitHubEventsApi(client);
        if (queryParams.isEmpty()) {
            return eventsApi.onEvent(Collections.emptyMap(), "abc", event, payloadMap);
        } else {
            return sendWithQueryParams(eventsApi, payloadMap, event, queryParams);
        }
    }

    private String sendWithQueryParams(GitHubEventsApi eventsApi, Map<String, Object> payload, String event, Map<String, String> queryParams) throws ApiException {
        GitHubEventsApi api = new GitHubEventsApi(getApiClient());
        return api.onEvent(queryParams, "abc", event, payload);
    }

    protected void assertLog(ProcessEntry entry, String pattern) throws Exception {
        byte[] ab = getLog(entry.getInstanceId());
        ServerClient.assertLog(pattern, ab);
    }

    protected void waitForCompletion(ProcessEntry entry) throws Exception {
        ServerClient.waitForCompletion(getApiClient(), entry.getInstanceId());
    }

    protected void deleteOrg(String orgName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.deleteOrg(orgName, "yes");
    }

    protected static String resourceToString(String resource) throws Exception {
        return ITUtils.resourceToString(AbstractGitHubTriggersIT.class, resource);
    }

    protected boolean isFinished(StatusEnum status) {
        return status == StatusEnum.CANCELLED ||
                status == StatusEnum.FAILED ||
                status == StatusEnum.FINISHED ||
                status == StatusEnum.TIMED_OUT;
    }
}
