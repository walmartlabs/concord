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
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.client.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.GitHubUtils;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Covers the following cases:
 * <pre>
 *     # project A
 *     # a default onPush trigger for the default branch
 *     triggers:
 *       - github:
 *           entryPoint: onPush
 *
 *     # project B
 *     # a default onPush trigger for the configured branch
 *     triggers:
 *       - github
 *           entryPoint: onPush
 *
 *     # project C
 *     # subscribed to the project A
 *     triggers:
 *       - github:
 *           org: "project A org"
 *           project: "project A"
 *           repository: ".*"
 *           entryPoint: onPush
 *
 *     # project D
 *     # subscribed to all registered projects
 *     triggers:
 *       - github:
 *           org: ".*"
 *           project: ".*"
 *           repository: ".*"
 *           entryPoint: onPush
 *
 *     # project E
 *     # subscribed to unknown repos only
 *     triggers:
 *       - github:
 *           org: ".*"
 *           project: ".*"
 *           repository: ".*"
 *           unknownRepo: true
 *           entryPoint: "onPush"
 *
 *     # project F
 *     # listens for both known and unknown repos
 *     triggers:
 *       - github:
 *           org: ".*"
 *           project: ".*"
 *           repository: ".*"
 *           unknownRepo: [true, false]
 *           entryPoint: "onPush"
 *
 *     # project G
 *     # accepts only specific commit authors
 *     triggers:
 *       - github:
 *           author: ".*xyz.*"
 *           entryPoint: onPush
 * </pre>
 */
public class GitHubTriggersIT extends AbstractServerIT {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX", Locale.US);

    @After
    public void tearDown() throws Exception {
        waitForProcessesToFinish();
    }

    /**
     * Tests the default branch behaviour:
     * <pre>
     * # project A
     * # a default onPush trigger for the default branch
     * triggers:
     *   - github:
     *       entryPoint: onPush
     * </pre>
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testDefaultBranch() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github", null);

        // ---

        deleteOrg(orgXName);
    }

    /**
     * Tests push into a custom branch:
     * <pre>
     *  # project A
     *  # a default onPush trigger for the default branch
     *  triggers:
     *    - github:
     *        entryPoint: onPush
     *
     *  # project B
     *  # a default onPush trigger for the configured branch
     *  triggers:
     *    - github
     *        entryPoint: onPush
     * </pre>
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testCustomBranch() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        Path repo = initRepo("githubTests/repos/defaultTrigger");
        String branch = "branch_" + randomString();
        createNewBranch(repo, branch, "githubTests/repos/defaultTriggerV2");

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, repo);
        refreshRepo(orgXName, projectAName, repoAName);

        // Project B
        // custom branch + a default trigger
        String projectBName = "projectB_" + randomString();
        String repoBName = "repoB_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectBName, repoBName, branch, repo);
        refreshRepo(orgXName, projectBName, repoBName);

        // ---

        // send an event for the master branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/master");

        // the default branch push must trigger the project A
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);

        // no project B processes should be triggered
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/" + branch);

        // there should be a project B's process
        waitForAProcess(orgXName, projectBName, "github", null);

        // and no additional processes in A
        expectNoProceses(orgXName, projectAName, procA);

        // ---

        deleteOrg(orgXName);
    }

    /**
     * Test subscription to another project:
     * <pre>
     * # project A
     * # a default onPush trigger for the default branch
     * triggers:
     *   - github:
     *       entryPoint: onPush
     *
     * # project C
     * # subscribed to the project A
     * triggers:
     *   - github:
     *       org: "project A org"
     *       project: "project A"
     *       repository: ".*"
     *       entryPoint: onPush
     * </pre>
     *
     * @throws Exception
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSubscribingToAProject() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project C
        // subscribed to the project A's push events
        String projectCName = "projectC_" + randomString();
        String repoCName = "repoC_" + randomString();
        Path projectCRepo = initProjectAndRepo(orgXName, projectCName, repoCName, null, initRepo("githubTests/repos/subscribeToProject"));
        updateConcordYml(projectCRepo, ImmutableMap.of(
                "_ORG_NAME", orgXName,
                "_PROJECT_NAME", projectAName));
        refreshRepo(orgXName, projectCName, repoCName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master");

        // A's and C's triggers should be activated
        waitForAProcess(orgXName, projectAName, "github", null);
        waitForAProcess(orgXName, projectCName, "github", null);

        // ---

        deleteOrg(orgXName);
    }

    /**
     * Test subscription to all registered projects.
     * <pre>
     *  # project A
     *  # a default onPush trigger for the default branch
     *  triggers:
     *    - github:
     *        entryPoint: onPush
     *
     *  # project B
     *  # a default onPush trigger for the configured branch
     *  triggers:
     *    - github
     *        entryPoint: onPush
     *
     *  # project D
     *  # subscribed to all registered projects
     *  triggers:
     *    - github:
     *        org: ".*"
     *        project: ".*"
     *        repository: ".*"
     *        entryPoint: onPush
     * </pre>
     *
     * @throws Exception
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSubscribingToAllRegisteredProjects() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        String orgYName = "orgY_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgYName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project B
        // custom branch + a default trigger
        String projectBName = "projectB_" + randomString();
        String repoBName = "repoB_" + randomString();
        String repoBBranch = "branchB_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectBName, repoBName, repoBBranch, initRepo("githubTests/repos/defaultTrigger"));
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTriggerV2");
        refreshRepo(orgXName, projectBName, repoBName);

        // Project D (in org Y)
        // subscribed to all other projects
        String projectDName = "projectD_" + randomString();
        String repoDName = "repoD_" + randomString();
        Path projectDRepo = initProjectAndRepo(orgYName, projectDName, repoDName, null, initRepo("githubTests/repos/subscribeToProject"));
        updateConcordYml(projectDRepo, ImmutableMap.of(
                "_ORG_NAME", ".*",
                "_PROJECT_NAME", ".*"));
        refreshRepo(orgYName, projectDName, repoDName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master");

        // A's and D's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        waitForAProcess(orgYName, projectDName, "github", null);

        // no B's are expected
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/" + repoBBranch);

        // B's and D's triggers should be activated
        waitForAProcess(orgXName, projectBName, "github", null);
        // TODO broken: doesn't trigger for the other repo's branch
        // waitForAProcess(orgYName, projectDName, "github", null);

        // no A's are expected
        expectNoProceses(orgXName, projectAName, procA);

        // ---

        deleteOrg(orgXName);
        deleteOrg(orgYName);
    }

    /**
     * Test subscription to unknown repositories only:
     * <pre>
     * # project A
     * # a default onPush trigger for the default branch
     * triggers:
     *   - github:
     *       entryPoint: onPush
     *
     * # project B
     * # a default onPush trigger for the configured branch
     * triggers:
     *   - github
     *       entryPoint: onPush
     *
     * # project E
     * # subscribed to unknown repos only
     * triggers:
     *   - github:
     *       org: ".*"
     *       project: ".*"
     *       repository: ".*"
     *       unknownRepo: true
     *       entryPoint: "onPush"
     * </pre>
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testUnknownReposOnly() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        String orgZName = "orgZ_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgZName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project B
        // custom branch + a default trigger
        String projectBName = "projectB_" + randomString();
        String repoBName = "repoB_" + randomString();
        String repoBBranch = "branchB_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectBName, repoBName, repoBBranch, initRepo("githubTests/repos/defaultTrigger"));
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTriggerV2");
        refreshRepo(orgXName, projectBName, repoBName);

        // Project E (in org Z)
        // subscribed to unknown repos only
        String projectEName = "projectE_" + randomString();
        String repoEName = "repoE_" + randomString();
        Path projectERepo = initProjectAndRepo(orgZName, projectEName, repoEName, null, initRepo("githubTests/repos/subscribeToUnknown"));
        refreshRepo(orgZName, projectEName, repoEName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        expectNoProceses(orgXName, projectBName, null);
        expectNoProceses(orgZName, projectEName, null);

        // ---

        // send a project B event for the master branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/master");

        // no project B processes should be triggered
        expectNoProceses(orgXName, projectBName, null);

        // no A's either (since the first trigger)
        expectNoProceses(orgXName, projectAName, procA);

        // expect a E's process (because of the branch mismatch the event is considered an "unknown repo")
        ProcessEntry procE = waitForAProcess(orgZName, projectEName, "github", null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/" + repoBBranch);

        // there should be a project B's process
        ProcessEntry procB = waitForAProcess(orgXName, projectBName, "github", procA);

        // no project A's or E's processes are expected (since the first trigger)
        expectNoProceses(orgXName, projectAName, procA);
        expectNoProceses(orgZName, projectEName, procE);

        // ---

        // send an unknown repo event
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "/unknown/repo",
                "_REF", "refs/heads/master");

        // E's trigger should be activated
        procE = waitForAProcess(orgZName, projectEName, "github", procE);

        expectNoProceses(orgXName, projectAName, procA);
        expectNoProceses(orgXName, projectBName, procB);

        // ---

        deleteOrg(orgXName);
        deleteOrg(orgZName);
    }

    /**
     * Test subscription to unknown repositories only:
     * <pre>
     * # project A
     * # a default onPush trigger for the default branch
     * triggers:
     *   - github:
     *       entryPoint: onPush
     *
     * # project B
     * # a default onPush trigger for the configured branch
     * triggers:
     *   - github
     *       entryPoint: onPush
     *
     * # project F
     * # listens for both known and unknown repos
     * triggers:
     *   - github:
     *       org: ".*"
     *       project: ".*"
     *       repository: ".*"
     *       unknownRepo: [true, false]
     *       entryPoint: "onPush"
     * </pre>
     */
    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testSubscriptionForBothKnownAndUnknownRepos() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        String orgZName = "orgZ_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgZName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project B
        // custom branch + a default trigger
        String projectBName = "projectB_" + randomString();
        String repoBName = "repoB_" + randomString();
        String repoBBranch = "branchB_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectBName, repoBName, repoBBranch, initRepo("githubTests/repos/defaultTrigger"));
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTriggerV2");
        refreshRepo(orgXName, projectBName, repoBName);

        // Project F (in org Z)
        // subscribed to both known and unknown repos
        String projectFName = "projectF_" + randomString();
        String repoFName = "repoF_" + randomString();
        Path projectFRepo = initProjectAndRepo(orgZName, projectFName, repoFName, null, initRepo("githubTests/repos/subscribeToAll"));
        refreshRepo(orgZName, projectFName, repoFName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master");

        // A's and F's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        ProcessEntry procF = waitForAProcess(orgZName, projectFName, "github", null);
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/" + repoBBranch);

        // there should be a project B's and F's processes
        ProcessEntry procB = waitForAProcess(orgXName, projectBName, "github", procA);
        procF = waitForAProcess(orgZName, projectFName, "github", procF);

        // no project A's processes are expected (since the first trigger)
        expectNoProceses(orgXName, projectAName, procA);

        // ---

        deleteOrg(orgXName);
        deleteOrg(orgZName);
    }

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
    public void testFilterByAuthor() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        Path repo = initRepo("githubTests/repos/defaultTrigger");
        String branch = "branch_" + randomString();
        createNewBranch(repo, branch, "githubTests/repos/defaultTriggerWithAuthor");

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project G
        // accepts only specific commit authors
        String projectGName = "projectG_" + randomString();
        String repoGName = "repoG_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectGName, repoGName, null, initRepo("githubTests/repos/defaultTriggerWithAuthor"));
        refreshRepo(orgXName, projectGName, repoGName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectARepo.toString(),
                "_REF", "refs/heads/master",
                "_USER_NAME", "aknowndude");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        expectNoProceses(orgXName, projectGName, null);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", projectBRepo.toString(),
                "_REF", "refs/heads/master",
                "_USER_NAME", "somecooldude");

        // G's triggers should be activated
        waitForAProcess(orgXName, projectGName, "github", null);

        // no A's are expected
        expectNoProceses(orgXName, projectAName, procA);

        // ---

        deleteOrg(orgXName);
    }

    private Path initRepo(String resource) throws Exception {
        Path src = Paths.get(GitHubTriggersIT.class.getResource(resource).toURI());
        return GitUtils.createBareRepository(src);
    }

    private Path initProjectAndRepo(String orgName, String projectName, String repoName, String repoBranch, Path bareRepo) throws Exception {
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());

        RepositoryEntry repo = new RepositoryEntry()
                .setBranch(repoBranch)
                .setUrl(bareRepo.toAbsolutePath().toString());

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setAcceptsRawPayload(true)
                .setRepositories(ImmutableMap.of(repoName, repo)));

        return bareRepo;
    }

    private void createNewBranch(Path bareRepo, String branch, String resource) throws Exception {
        Path dir = IOUtils.createTempDir("git");

        Git git = Git.cloneRepository()
                .setDirectory(dir.toFile())
                .setURI(bareRepo.toAbsolutePath().toString())
                .call();

        git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .call();

        Path src = Paths.get(GitHubTriggersIT.class.getResource(resource).toURI());
        IOUtils.copy(src, dir, StandardCopyOption.REPLACE_EXISTING);

        git.add()
                .addFilepattern(".")
                .call();

        git.commit()
                .setMessage("adding files from " + resource)
                .call();

        git.push()
                .setRefSpecs(new RefSpec(branch + ":" + branch))
                .call();
    }

    private void updateConcordYml(Path bareRepo, Map<String, String> values) throws Exception {
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

    private void refreshRepo(String orgName, String projectName, String repoName) throws Exception {
        RepositoriesApi repoApi = new RepositoriesApi(getApiClient());
        repoApi.refreshRepository(orgName, projectName, repoName, true);
    }

    private ProcessEntry waitForAProcess(String orgName, String projectName, String initiator, ProcessEntry after) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        while (true) {
            String afterCreatedAt = after != null ? after.getCreatedAt().format(DATE_TIME_FORMATTER) : null;
            List<ProcessEntry> l = processApi.list(null, orgName, null, projectName, afterCreatedAt, null, null, null, initiator, null, null, null, null);
            if (l.size() == 1) {
                return l.get(0);
            }

            Thread.sleep(1000);
        }
    }

    private int waitForProcessesToFinish() throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        while (true) {
            List<ProcessEntry> l = processApi.list(null, null, null, null, null, null, null, null, null, null, null, null, null);

            boolean allDone = true;
            for (ProcessEntry e : l) {
                if (e.getStatus() == StatusEnum.PREPARING
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

    private void expectNoProceses(String orgName) throws Exception {
        expectNoProceses(orgName, null, null);
    }

    private void expectNoProceses(String orgName, String projectName, ProcessEntry after) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        String afterCreatedAt = after != null ? after.getCreatedAt().format(DATE_TIME_FORMATTER) : null;
        List<ProcessEntry> l = processApi.list(null, orgName, null, projectName, afterCreatedAt, null, null, null, null, null, null, null, null);
        assertEquals(0, l.size());
    }

    private void sendEvent(String resource, String event, String... params) throws Exception {
        String payload = resourceToString("githubTests/events/direct_branch_push.json");
        if (params != null) {
            for (int i = 0; i < params.length; i += 2) {
                String k = params[i];
                String v = params[i + 1];
                payload = payload.replaceAll(k, v);
            }
        }

        ApiClient client = getApiClient();
        client.addDefaultHeader("X-Hub-Signature", "sha1=" + GitHubUtils.sign(payload));

        GitHubEventsApi eventsApi = new GitHubEventsApi(client);
        eventsApi.onEvent(payload, "abc", event);
    }

    private void assertLog(ProcessEntry entry, String pattern) throws Exception {
        byte[] ab = getLog(entry.getLogFileName());
        ServerClient.assertLog(pattern, ab);
    }

    private void waitForCompletion(ProcessEntry entry) throws Exception {
        ProcessApi processApi = new ProcessApi(getApiClient());
        ServerClient.waitForCompletion(processApi, entry.getInstanceId());
    }

    private void deleteOrg(String orgName) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        orgApi.delete(orgName, "yes");
    }

    private static String resourceToString(String resource) throws Exception {
        URL url = GitHubTriggersIT.class.getResource(resource);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = url.openStream()) {
            IOUtils.copy(in, out);
        }

        return new String(out.toByteArray());
    }
}
