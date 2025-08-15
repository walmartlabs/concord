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
import com.walmartlabs.concord.client2.CreateSecretRequest;
import com.walmartlabs.concord.client2.ProcessListFilter;
import com.walmartlabs.concord.common.IOUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.walmartlabs.concord.common.GrepUtils.grep;
import static com.walmartlabs.concord.it.server.AbstractServerIT.DEFAULT_TEST_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Timeout(value = 2 * DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public class CronIT extends AbstractServerIT {

    @Test
    public void testProfiles() throws Exception {
        String gitUrl = initRepo("cronProfiles");

        // ---

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        if (orgApi.getOrg(orgName) == null) {
            orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        }

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));

        // ---

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        Set<String> expectedPatterns = new HashSet<>();
        expectedPatterns.add(".*Hello, AAA!.*");
        expectedPatterns.add(".*Hello, BBB!.*");

        while (true) {
            Thread.sleep(1000);

            List<ProcessEntry> aaaProcesses = listCronProcesses(orgName, projectName, repoName, "AAA");
            List<ProcessEntry> bbbProcesses = listCronProcesses(orgName, projectName, repoName, "BBB");

            if (aaaProcesses.isEmpty() || bbbProcesses.isEmpty()) {
                continue;
            }

            List<ProcessEntry> processes = Stream.concat(aaaProcesses.stream(), bbbProcesses.stream())
                    .collect(Collectors.toList());

            for (ProcessEntry e : processes) {
                assertNotEquals(ProcessEntry.StatusEnum.FAILED, e.getStatus());
            }

            Set<String> patterns = new HashSet<>(expectedPatterns);
            for (String p : patterns) {
                for (ProcessEntry e : processes) {
                    if (hasLogEntry(e, p)) {
                        expectedPatterns.remove(p);
                    }
                }
            }

            if (expectedPatterns.isEmpty()) {
                break;
            }
        }

        // --- clean up

        projectsApi.deleteProject(orgName, projectName);
    }

    @Test
    public void testRunAs() throws Exception {
        String gitUrl = initRepo("cronRunAs");

        // ---

        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();

        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        if (orgApi.getOrg(orgName) == null) {
            orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        }

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));

        // ---

        String username = "user_" + randomString();

        UsersApi usersApi = new UsersApi(getApiClient());
        CreateUserResponse cur = usersApi.createOrUpdateUser(new CreateUserRequest()
                .username(username)
                .type(CreateUserRequest.TypeEnum.LOCAL));

        ApiKeysApi apiKeyResource = new ApiKeysApi(getApiClient());
        CreateApiKeyResponse apiKeyResponse = apiKeyResource.createUserApiKey(new CreateApiKeyRequest().username(username));

        com.walmartlabs.concord.client2.SecretClient secretsApi = new com.walmartlabs.concord.client2.SecretClient(getApiClient());
        CreateSecretRequest secret = CreateSecretRequest.builder()
                .org(orgName)
                .addProjectNames(projectName)
                .name("test-run-as")
                .data(apiKeyResponse.getKey().getBytes(StandardCharsets.UTF_8))
                .build();
        secretsApi.createSecret(secret);

        waitForTriggers(orgName, projectName, repoName, 1);

        // ---

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(orgName)
                .projectName(projectName)
                .build();

        while (true) {
            Thread.sleep(1000);

            List<ProcessEntry> processes = processApi.listProcesses(filter);
            if (processes.size() != 1) {
                continue;
            }

            ProcessEntry pe = processes.get(0);
            assertNotEquals(ProcessEntry.StatusEnum.FAILED, pe.getStatus());
            assertEquals(cur.getId(), pe.getInitiatorId());
            assertEquals(username, pe.getInitiator());
            break;
        }

        // ---

        projectsApi.deleteProject(orgName, projectName);
    }

    private static String initRepo(String initResource) throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource(initResource).toURI());
        IOUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
            return tmpDir.toAbsolutePath().toString();
        }
    }

    private List<ProcessEntry> listCronProcesses(String o, String p, String r, String tag) throws ApiException {
        ProcessV2Api processV2Api = new ProcessV2Api(getApiClient());

        ProcessListFilter filter = ProcessListFilter.builder()
                .orgName(o)
                .projectName(p)
                .repoName(r)
                .initiator("cron")
                .tags(Collections.singleton(tag))
                .build();

        return processV2Api.listProcesses(filter);
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

    private boolean hasLogEntry(ProcessEntry e, String pattern) throws Exception {
        byte[] ab = getLog(e.getInstanceId());
        List<String> l = grep(pattern, ab);
        return !l.isEmpty();
    }
}
