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
import com.walmartlabs.concord.client.OrganizationEntry;
import com.walmartlabs.concord.client.OrganizationsApi;
import com.walmartlabs.concord.client.ProcessEntry;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Path;

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
public class GitHubTriggersIT extends AbstractGitHubTriggersIT {

    @After
    public void tearDown() throws Exception {
        waitForProcessesToFinish();
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
        createNewBranch(repo, branch, "githubTests/repos/defaultTrigger");

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
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // the default branch push must trigger the project A
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);

        // no project B processes should be triggered
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
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
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
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
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTrigger");
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
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's and D's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        waitForAProcess(orgYName, projectDName, "github", null);

        // no B's are expected
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
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
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTrigger");
        refreshRepo(orgXName, projectBName, repoBName);

        // Project E (in org Z)
        // subscribed to unknown repos only
        String projectEName = "projectE_" + randomString();
        String repoEName = "repoE_" + randomString();
        Path projectERepo = initProjectAndRepo(orgZName, projectEName, repoEName, null, initRepo("githubTests/repos/subscribeToUnknown"));
        refreshRepo(orgZName, projectEName, repoEName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        expectNoProceses(orgXName, projectBName, null);
        expectNoProceses(orgZName, projectEName, null);

        // ---

        // send a project B event for the master branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
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
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
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
                "_USER_LDAP_DN", "",
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
        createNewBranch(projectBRepo, repoBBranch, "githubTests/repos/defaultTrigger");
        refreshRepo(orgXName, projectBName, repoBName);

        // Project F (in org Z)
        // subscribed to both known and unknown repos
        String projectFName = "projectF_" + randomString();
        String repoFName = "repoF_" + randomString();
        Path projectFRepo = initProjectAndRepo(orgZName, projectFName, repoFName, null, initRepo("githubTests/repos/subscribeToAll"));
        refreshRepo(orgZName, projectFName, repoFName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's and F's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        ProcessEntry procF = waitForAProcess(orgZName, projectFName, "github", null);
        expectNoProceses(orgXName, projectBName, null);

        // ---

        // send a project B event for the project's branch
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_USER_LDAP_DN", "",
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
}
