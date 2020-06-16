package com.walmartlabs.concord.it.server.v2;

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

import com.walmartlabs.concord.client.OrganizationEntry;
import com.walmartlabs.concord.client.OrganizationsApi;
import com.walmartlabs.concord.client.ProcessEntry;
import com.walmartlabs.concord.it.server.AbstractGitHubTriggersIT;
import org.junit.After;
import org.junit.Test;

import java.nio.file.Path;

public class GitHubTriggersV2IT extends AbstractGitHubTriggersIT {

    @After
    public void tearDown() throws Exception {
        waitForProcessesToFinish();
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
    public void testFilterBySender() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        Path repo = initRepo("v2/triggers/github/repos/v2/defaultTrigger");
        String branch = "branch_" + randomString();
        createNewBranch(repo, branch, "v2/triggers/github/repos/v2/defaultTriggerWithSender");

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // Project G
        // accepts only specific commit authors
        String projectGName = "projectG_" + randomString();
        String repoGName = "repoG_" + randomString();
        Path projectBRepo = initProjectAndRepo(orgXName, projectGName, repoGName, null, initRepo("githubTests/repos/v2/defaultTriggerWithSender"));
        refreshRepo(orgXName, projectGName, repoGName);

        // ---

        sendEvent("v2/triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "aknowndude",
                "_USER_LDAP_DN", "");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github", null);
        expectNoProceses(orgXName, projectGName, null);

        // ---

        sendEvent("v2/triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "somecooldude",
                "_USER_LDAP_DN", "");

        // G's triggers should be activated
        waitForAProcess(orgXName, projectGName, "github", null);

        // no A's are expected
        expectNoProceses(orgXName, projectAName, procA);

        // ---

        deleteOrg(orgXName);
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
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("v2/triggers/github/repos/v2/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("v2/triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github", null);

        // ---

        deleteOrg(orgXName);
    }

    @Test(timeout = DEFAULT_TEST_TIMEOUT)
    public void testOnPushWithFullTriggerParams() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdate(new OrganizationEntry().setName(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("v2/triggers/github/repos/v2/allParamsTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("v2/triggers/github/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "devtools/concord",
                "_REF", "refs/heads/master",
                "_USER_NAME", "vasia",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github", null);

        // ---

        deleteOrg(orgXName);
    }
}
