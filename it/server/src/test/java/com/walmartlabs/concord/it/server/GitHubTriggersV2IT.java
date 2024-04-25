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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.client2.ProcessListFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.naming.NameAlreadyBoundException;
import javax.naming.directory.*;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class GitHubTriggersV2IT extends AbstractGitHubTriggersIT {

    @AfterEach
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
    @Test
    public void testFilterBySender() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        Path repo = initRepo("githubTests/repos/v2/defaultTrigger");
        String branch = "branch_" + randomString();
        createNewBranch(repo, branch, "githubTests/repos/v2/defaultTriggerWithSender");

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

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "aknowndude",
                "_USER_LDAP_DN", "");

        // A's triggers should be activated
        ProcessEntry procA = waitForAProcess(orgXName, projectAName, "github");
        expectNoProceses(orgXName, projectGName, null);

        // ---

        // see https://github.com/walmartlabs/concord/issues/435
        // wait a bit to reliably filter out subsequent processes of projectA
        Thread.sleep(1000);
        OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectBRepo),
                "_REF", "refs/heads/master",
                "_USER_NAME", "somecooldude",
                "_USER_LDAP_DN", "");

        // G's triggers should be activated
        waitForAProcess(orgXName, projectGName, "github");

        // no A's are expected
        expectNoProceses(orgXName, projectAName, now);

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
    @Test
    public void testDefaultBranch() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/defaultTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github");

        // ---

        deleteOrg(orgXName);
    }

    @Test
    public void testOnPushWithFullTriggerParams() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/allParamsTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "devtools/concord",
                "_REF", "refs/heads/master",
                "_USER_NAME", "vasia",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github");

        // ---

        deleteOrg(orgXName);
    }

    @Test
    public void testOnPushWithFiles() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/files"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", "devtools/concord",
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        waitForAProcess(orgXName, projectAName, "github");

        // ---

        deleteOrg(orgXName);
    }

    /**
     * Verify that the "requestInfo" variable is available for GitHub processes
     * (should be empty).
     */
    @Test
    public void testRequestInfo() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/requestInfo"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        ProcessEntry pe = waitForAProcess(orgXName, projectAName, "github");

        assertLog(pe, ".*Hello, !.*");

        // ---

        deleteOrg(orgXName);
    }

    @Test
    public void testQueryParamsCondition() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/queryParams"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                Collections.singletonMap("param1", "value1"),
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        // A's trigger should be activated
        ProcessEntry pe = waitForAProcess(orgXName, projectAName, "github");

        assertLog(pe, ".*Hello, value1!.*");

        // ---

        deleteOrg(orgXName);
    }

    @Test
    public void testIgnoreEmptyPush() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        Path projectRepo = initProjectAndRepo(orgName, projectName, repoName, null, initRepo("githubTests/repos/v2/ignoreEmptyPushTrigger"));
        refreshRepo(orgName, projectName, repoName);

        // ---

        sendEvent("githubTests/events/empty_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectRepo),
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        ProcessEntry pe = waitForAProcess(orgName, projectName, "github");
        assertLog(pe, ".*onEmpty: .*");

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .build();

        List<ProcessEntry> list = processApi.listProcesses(filter);
        assertEquals(1, list.size());

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_FULL_REPO_NAME", toRepoName(projectRepo),
                "_REF", "refs/heads/master",
                "_USER_LDAP_DN", "");

        while (true) {
            list = processApi.listProcesses(filter);
            if (list.size() == 3) {
                break;
            }

            Thread.sleep(1000);
        }
    }

    @Test
    public void testRefreshOnGitHubEvent() throws Exception {
        String username = createUser();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // -- create two projects to hold two similarly named repos

        String projectName1 = "project_" + randomString();
        String projectName2 = "project_" + randomString();
        String repoNameShort = "repo_" + randomString();
        String repoNameLong = repoNameShort + "-two";

        Path repoPathShort = initRepo("githubTests/repos/v2/defaultTrigger", orgName + "/" + repoNameShort);
        Path repoPathLong = initRepo("githubTests/repos/v2/defaultTrigger", orgName + "/" + repoNameLong);

        Path projectRepoShort = initProjectAndRepo(orgName, projectName1, repoNameShort, null, repoPathShort);
        Path projectRepoLong = initProjectAndRepo(orgName, projectName2, repoNameLong, null, repoPathLong);
        waitForProcessesToFinish();

        // -- send GitHub event to trigger refresh

        OffsetDateTime after = OffsetDateTime.now();
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_USER_LDAP_DN", "",
                "_USER_NAME", username,
                "_ORG_NAME", orgName,
                "_FULL_REPO_NAME", toRepoName(projectRepoShort), // must be before _REPO_NAME
                "_REPO_NAME", repoNameShort,
                "_REF", "refs/heads/master");

        // -- locate and wait for repository refresh process

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName("ConcordSystem")
                .projectName("concordTriggers")
                .limit(1)
                .offset(0)
                .afterCreatedAt(after)
                .build();
        ProcessEntry refreshProc;

        while (true) {
            refreshProc = processApi.listProcesses(filter).stream()
                    .filter(e -> e.getInitiator().equals("github"))
                    .findFirst()
                    .orElse(null);

            if (refreshProc != null && refreshProc.getStatus() == ProcessEntry.StatusEnum.FINISHED) {
                break;
            }
            Thread.sleep(500);
        }

        assertNotNull(refreshProc, "Must find repository refresh process.");
        assertEquals(ProcessEntry.StatusEnum.FINISHED, refreshProc.getStatus(),
                "Repository refresh process must finish successfully.");

        // -- process log should indicate only one repo was refreshed

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectEntry p = projectsApi.getProject(orgName, projectName1);

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        List<RepositoryEntry> repos = repositoriesApi.listRepositories(orgName, projectName1, null, null, null);

        RepositoryEntry repo = repos.stream()
                .filter(e -> e.getName().equals(repoNameShort))
                .findFirst()
                .orElseThrow(() -> new Exception("Unable to locate repository"));

        assertLog(refreshProc, ".*Repository ids to refresh: \\[" + repo.getId().toString() + "\\].*");
    }

    @Test
    public void testBranchDeleteRefresh() throws Exception {
        String username = createUser();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // -- create two projects to hold two similarly named repos

        String projectNameMaster = "project_master" + randomString();
        String projectNameDev = "project_dev_" + randomString();
        String projectNameTmp = "project_tmp_" + randomString();
        String repoMaster = "repo_master_" + randomString();
        String repoDev = "repo_dev_" + randomString();
        String repoTmp = "repo_tmp_" + randomString();
        String devBranch = "dev";
        String tmpBranch = "tmp";

        Path repoPath = initRepo("githubTests/repos/v2/defaultTrigger", orgName + "/" + repoMaster);
        // we only need the repo to exist enough to create the concord repo
        createNewBranch(repoPath, devBranch, "githubTests/repos/v2/defaultTrigger");
        createNewBranch(repoPath, tmpBranch, "githubTests/repos/v2/defaultTrigger");

        Path projectRepoMain = initProjectAndRepo(orgName, projectNameMaster, repoMaster, null, repoPath);
        Path projectRepoDev = initProjectAndRepo(orgName, projectNameDev, repoDev, devBranch, repoPath);
        Path projectRepoTmp = initProjectAndRepo(orgName, projectNameTmp, repoTmp, tmpBranch, repoPath);
        waitForProcessesToFinish();

        // -- send GitHub event to trigger refresh

        OffsetDateTime after = OffsetDateTime.now();
        sendEvent("githubTests/events/direct_branch_push_delete.json", "push",
                "_USER_LDAP_DN", "",
                "_USER_NAME", username,
                "_ORG_NAME", orgName,
                "_FULL_REPO_NAME", toRepoName(projectRepoMain), // must be before _REPO_NAME
                "_REPO_NAME", repoMaster,
                "_REF", "refs/heads/tmp");

        // -- locate and wait for repository refresh process
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName("ConcordSystem")
                .projectName("concordTriggers")
                .limit(1)
                .offset(0)
                .afterCreatedAt(after)
                .build();
        ProcessEntry refreshProc;

        // not great, but we need to ensure no processes were generated
        Thread.sleep(3000);

        refreshProc = processApi.listProcesses(filter).stream()
                    .filter(e -> e.getInitiator().equals("github"))
                    .findFirst()
                    .orElse(null);

        assertNull(refreshProc, "Must NOT find repository refresh process.");
    }

    @Test
    public void testBranchRefreshMatchingOnly() throws Exception {
        String username = createUser();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        // -- create two projects to hold two similarly named repos

        String projectName1 = "project_master" + randomString();
        String projectName2 = "project_dev_" + randomString();
        String repoMaster = "repo_main_" + randomString();
        String repoDev = "repo_dev_" + randomString();
        String devBranch = "dev";

        Path repoPath = initRepo("githubTests/repos/v2/defaultTrigger", orgName + "/" + repoMaster);
        // we only need the repo to exist enough to create the concord repo
        createNewBranch(repoPath, devBranch, "githubTests/repos/v2/defaultTrigger");

        Path projectRepoMain = initProjectAndRepo(orgName, projectName1, repoMaster, null, repoPath);
        Path projectRepoDev = initProjectAndRepo(orgName, projectName2, repoDev, devBranch, repoPath);
        waitForProcessesToFinish();

        // -- send GitHub event to trigger refresh

        OffsetDateTime after = OffsetDateTime.now();
        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_USER_LDAP_DN", "",
                "_USER_NAME", username,
                "_ORG_NAME", orgName,
                "_FULL_REPO_NAME", toRepoName(projectRepoMain), // must be before _REPO_NAME
                "_REPO_NAME", repoMaster,
                "_REF", "refs/heads/master");

        // -- locate and wait for repository refresh process

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName("ConcordSystem")
                .projectName("concordTriggers")
                .limit(1)
                .offset(0)
                .afterCreatedAt(after)
                .build();
        ProcessEntry refreshProc;

        while (true) {
            refreshProc = processApi.listProcesses(filter).stream()
                    .filter(e -> e.getInitiator().equals("github"))
                    .findFirst()
                    .orElse(null);

            if (refreshProc != null && refreshProc.getStatus() == ProcessEntry.StatusEnum.FINISHED) {
                break;
            }
            Thread.sleep(500);
        }

        assertNotNull(refreshProc, "Must find repository refresh process.");
        assertEquals(ProcessEntry.StatusEnum.FINISHED, refreshProc.getStatus(),
                "Repository refresh process must finish successfully.");

        // -- process log should indicate only default repo was refreshed

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        List<RepositoryEntry> repos = repositoriesApi.listRepositories(orgName, projectName1, null, null, null);

        RepositoryEntry repo = repos.stream()
                .filter(e -> e.getName().equals(repoMaster))
                .findFirst()
                .orElseThrow(() -> new Exception("Unable to locate repository"));

        assertLog(refreshProc, ".*Repository ids to refresh: \\[" + repo.getId().toString() + "\\].*");
    }

    @Test
    public void testUseInitiatorFromSender() throws Exception {
        String username = createUser();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        Path projectRepo = initProjectAndRepo(orgName, projectName, repoName, null, initRepo("githubTests/repos/v2/useInitiatorTrigger"));
        refreshRepo(orgName, projectName, repoName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_USER_LDAP_DN", "",
                "_USER_NAME", username,
                "_FULL_REPO_NAME", toRepoName(projectRepo),
                "_REF", "refs/heads/master");

        ProcessEntry pe = waitForAProcess(orgName, projectName, username);
        assertEquals(username, pe.getInitiator());
    }

    @Test
    public void testUseInitiatorFromSenderLdapDn() throws Exception {
        String username = createUser();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgName = "org_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        Path projectRepo = initProjectAndRepo(orgName, projectName, repoName, null, initRepo("githubTests/repos/v2/useInitiatorTrigger"));
        refreshRepo(orgName, projectName, repoName);

        // ---

        sendEvent("githubTests/events/direct_branch_push.json", "push",
                "_USER_LDAP_DN", "cn=" + username + ",dc=example,dc=org",
                "_FULL_REPO_NAME", toRepoName(projectRepo),
                "_REF", "refs/heads/master");

        ProcessEntry pe = waitForAProcess(orgName, projectName, username);
        assertEquals(username, pe.getInitiator());
    }

    @Test
    public void testExclusiveGroupByBranch() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/groupByBranchTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/pr_open.json", "pull_request",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's trigger should be activated
        ProcessEntry pe = waitForAProcess(orgXName, projectAName, "github");

        assertLog(pe, ".*Process' exclusive group: master.*");

        // ---

        deleteOrg(orgXName);
    }

    @Test
    public void testExclusiveGroupByEventAttr() throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());

        String orgXName = "orgX_" + randomString();
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgXName));

        // Project A
        // master branch + a default trigger
        String projectAName = "projectA_" + randomString();
        String repoAName = "repoA_" + randomString();
        Path projectARepo = initProjectAndRepo(orgXName, projectAName, repoAName, null, initRepo("githubTests/repos/v2/groupByEventAttrTrigger"));
        refreshRepo(orgXName, projectAName, repoAName);

        // ---

        sendEvent("githubTests/events/pr_open.json", "pull_request",
                "_FULL_REPO_NAME", toRepoName(projectARepo),
                "_USER_LDAP_DN", "",
                "_REF", "refs/heads/master");

        // A's trigger should be activated
        ProcessEntry pe = waitForAProcess(orgXName, projectAName, "github");

        assertLog(pe, ".*Process' exclusive group: pr-test-3.*");

        // ---

        deleteOrg(orgXName);
    }

    private String createUser() throws Exception {
        assertNotNull(System.getenv("IT_LDAP_URL"));

        String username = "user_" + randomString();

        DirContext ldapCtx = LdapIT.createContext();
        createLdapUser(ldapCtx, username);

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LDAP));
        return username;
    }

    static void createLdapUser(DirContext ldapCtx, String username) throws Exception {
        String dn = "cn=" + username + ",dc=example,dc=org";
        Attributes attributes = new BasicAttributes();

        Attribute cn = new BasicAttribute("cn", username);
        Attribute sn = new BasicAttribute("sn", username);

        Attribute objectClass = new BasicAttribute("objectClass");
        objectClass.add("top");
        objectClass.add("organizationalPerson");

        attributes.put(cn);
        attributes.put(sn);
        attributes.put(objectClass);

        try {
            ldapCtx.createSubcontext(dn, attributes);
        } catch (NameAlreadyBoundException e) {
            System.err.println("createLdapUser -> " + e.getMessage());
            // already exists, ignore
        }
    }
}
