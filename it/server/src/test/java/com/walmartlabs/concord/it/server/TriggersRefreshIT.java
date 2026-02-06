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
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.sdk.Constants;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TriggersRefreshIT extends AbstractServerIT {

    @Test
    public void testTriggerRepoRefresh() throws Exception {
        Path tmpDir = createSharedTempDir();

        File src = new File(TriggersRefreshIT.class.getResource("triggerRepo").toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        String username = "user_" + randomString();

        // ---

        UsersApi usersApi = new UsersApi(getApiClient());
        usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeysApi = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse cakr = apiKeysApi.createUserApiKey(new CreateApiKeyRequest()
                .username(username));

        // ---

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        if (orgApi.getOrg(orgName) == null) {
            orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        }

        TeamsApi teamsApi = new TeamsApi(getApiClient());
        teamsApi.addUsersToTeam(orgName, "default", false, Collections.singletonList(new TeamUserEntry()
                .username(username)
                .role(TeamUserEntry.RoleEnum.MEMBER)
                .userType(TeamUserEntry.UserTypeEnum.LOCAL)));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));

        // ---

        List<TriggerEntry> l = waitForTriggers(orgName, projectName, repoName, 1);
        assertEquals("onTrigger", getEntryPoint(l.get(0)));

        // ---

        Files.copy(tmpDir.resolve("new_concord.yml"), tmpDir.resolve("concord.yml"), StandardCopyOption.REPLACE_EXISTING);

        try (Git repo = Git.open(tmpDir.toFile())) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("update").call();
        }

        // ---

        setApiKey(cakr.getKey());

        // ---

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        repositoriesApi.refreshRepository(orgName, projectName, repoName, true);

        // ---

        l = waitForTriggers(orgName, projectName, repoName, 2);
        l.sort(Comparator.comparing(TriggersRefreshIT::getEntryPoint));

        assertEquals("onTrigger", getEntryPoint(l.get(0)));
        assertEquals("onTrigger2", getEntryPoint(l.get(1)));
    }

    private List<TriggerEntry> waitForTriggers(String orgName, String projectName, String repoName, int expectedCount) throws Exception {
        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> l = triggerResource.listTriggers(orgName, projectName, repoName);
            if (l != null && l.size() == expectedCount) {
                return l;
            }

            Thread.sleep(1000);
        }
    }

    private static String getEntryPoint(TriggerEntry e) {
        Map<String, Object> cfg = e.getCfg();
        if (cfg == null) {
            return null;
        }
        return (String) cfg.get(Constants.Request.ENTRY_POINT_KEY);
    }
}
