package com.walmartlabs.concord.it.runtime.v2;

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

import ca.ibodrov.concord.testcontainers.ConcordProcess;
import ca.ibodrov.concord.testcontainers.ProcessListQuery;
import ca.ibodrov.concord.testcontainers.junit4.ConcordRule;
import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.GitHubUtils;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.ITUtils;
import org.junit.ClassRule;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static com.walmartlabs.concord.it.runtime.v2.ITConstants.DEFAULT_TEST_TIMEOUT;
import static org.junit.Assert.assertEquals;

public class GitHubTriggersV2IT {

    @ClassRule
    public static final ConcordRule concord = ConcordConfiguration.configure();

    /**
     * Test subscription to unknown repositories only:
     * <pre>
     * # project A
     * # a default onPush trigger for the default branch
     * triggers:
     *   - github:
     *       entryPoint: onPush
     *
     * # project G
     * # accepts only specific commit authors
     * triggers:
     *   - github:
     *       author: ".*xyz.*"
     *       entryPoint: onPush
     * </pre>
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testFilterBySender() throws Exception {
        String orgXName = "orgX_" + randomString();
        concord.organizations().create(orgXName);

        Path repo = initRepo("triggers/github/repos/v2/defaultTrigger");
        String branch = "branch_" + randomString();
        createNewBranch(repo, branch, "triggers/github/repos/v2/defaultTriggerWithSender");

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("triggers/github/repos/v2/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project G
        // accepts only specific commit authors
        String projectGName = "projectG_" + randomString();
        String repoGName = "repoG_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectGName, repoGName, null, initRepo("triggers/github/repos/v2/defaultTriggerWithSender"));
        refreshRepo(orgXName, projectGName, repoGName);

        // ---

        sendEvent("triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "aknowndude",
                "_USER_LDAP_DN", "");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github");
        expectNoProcesses(orgXName, projectGName, null);

        // ---

        sendEvent("triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "somecooldude",
                "_USER_LDAP_DN", "");

        // G's triggers should be activated
        waitForAProcess(orgXName, projectGName, "github");

        // no A's are expected
        expectNoProcesses(orgXName, projectAName, procA);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnPushWithFullTriggerParams() throws Exception {
        String orgXName = "orgX_" + randomString();
        concord.organizations().create(orgXName);

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("triggers/github/repos/v2/allParamsTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "devtools/concord",
                "_REF", "refs/heads/master",
                "_USER_NAME", "vasia",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnPushWithUseEventCommitId() throws Exception {
        //
        Path repo = initRepo("triggers/github/repos/v2/useEventCommitIdTrigger");
        String branch = "branch_" + randomString();
        String commitId = createNewBranch(repo, branch, "triggers/github/repos/v2/defaultTrigger");

        //
        String orgName = "orgX_" + randomString();
        concord.organizations().create(orgName);

        //
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        initProjectAndRepo(orgName, projectName, repoName, null, repo);
        refreshRepo(orgName, projectName, repoName);

        // ---

        sendEvent("triggers/github/events/direct_branch_push_commit_id.json", "push",
                "_FULL_REPO_NAME", toRepoName(repo),
                "_REF", "refs/heads/" + branch,
                "_USER_NAME", "vasia",
                "_USER_LDAP_DN", "",
                "_COMMIT_ID", commitId);

        //
        ProcessEntry pe = waitForAProcess(orgName, projectName, "github");
        ConcordProcess process = concord.processes().get(pe.getInstanceId());
        process.assertLog(".*onPush: .*" + commitId + ".*");
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnPushWithFilesCondition() throws Exception {
        String orgXName = "orgX_" + randomString();
        concord.organizations().create(orgXName);

        // Project A
        // master branch + a trigger with "files" condition
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("triggers/github/repos/v2/filesTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "devtools/concord",
                "_REF", "refs/heads/master",
                "_USER_NAME", "vasia",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github");
    }

    private static Path initRepo(String resource) throws Exception {
        Path src = Paths.get(GitHubTriggersV2IT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src, concord.sharedContainerDir());
    }

    private static String createNewBranch(Path bareRepo, String branch, String resource) throws Exception {
        Path src = Paths.get(GitHubTriggersV2IT.class.getResource(resource).toURI());
        return GitUtils.createNewBranch(bareRepo, branch, src);
    }

    private static Path initProjectAndRepo(String orgName, String projectName, String repoName, String repoBranch, Path bareRepo) throws Exception {
        // TODO: up concord test rule for projects with repository
        ProjectsApi projectsApi = new ProjectsApi(apiClient());

        RepositoryEntry repo = new RepositoryEntry()
                .setBranch(repoBranch != null ? repoBranch : "master")
                .setUrl(bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE)
                .setRepositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    private static void refreshRepo(String orgName, String projectName, String repoName) throws Exception {
        RepositoriesApi repoApi = new RepositoriesApi(apiClient());
        repoApi.refreshRepository(orgName, projectName, repoName, true);
    }

    private static void sendEvent(String resource, String event, String... params) throws Exception {
        String payload = resourceToString(resource);
        if (params != null) {
            for (int i = 0; i < params.length; i += 2) {
                String k = params[i];
                String v = params[i + 1];
                payload = payload.replaceAll(k, v);
            }
        }

        ApiClient client = apiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(payload));

        GitHubEventsApi eventsApi = new GitHubEventsApi(client);
        eventsApi.onEvent(payload, "abc", event);
    }

    private static String resourceToString(String resource) throws Exception {
        return ITUtils.resourceToString(GitHubTriggersV2IT.class, resource);
    }

    private static String toRepoName(Path p) {
        return p.getParent() .getFileName()+ "/" + p.getFileName();
    }

    private static ProcessEntry waitForAProcess(String orgName, String projectName, String initiator) throws Exception {
        ProcessListQuery q = ProcessListQuery.builder()
                .orgName(orgName)
                .projectName(projectName)
                .initiator(initiator)
                .build();

        while (!Thread.currentThread().isInterrupted()) {
            List<ProcessEntry> l = concord.processes().list(q);
            if (l.size() == 1 && isFinished(l.get(0).getStatus())) {
                return l.get(0);
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException("Process wait interrupted");
    }

    private static boolean isFinished(ProcessEntry.StatusEnum status) {
        return status == ProcessEntry.StatusEnum.CANCELLED ||
                status == ProcessEntry.StatusEnum.FAILED ||
                status == ProcessEntry.StatusEnum.FINISHED ||
                status == ProcessEntry.StatusEnum.TIMED_OUT;
    }

    private static void expectNoProcesses(String orgName, String projectName, ProcessEntry after) throws Exception {
        ProcessListQuery q = ProcessListQuery.builder()
                .orgName(orgName)
                .projectName(projectName)
                .afterCreatedAt(after != null ? after.getCreatedAt() : null)
                .build();

        List<ProcessEntry> l = concord.processes().list(q);
        assertEquals(0, l.size());
    }

    private static ApiClient apiClient() {
        return concord.apiClient();
    }
}
