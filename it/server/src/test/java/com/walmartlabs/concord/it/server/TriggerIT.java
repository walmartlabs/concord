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
import com.walmartlabs.concord.client2.ProcessEntry.StatusEnum;
import com.walmartlabs.concord.client2.ProcessListFilter;
import com.walmartlabs.concord.common.PathUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;

public class TriggerIT extends AbstractServerIT {

    @Test
    public void testTriggerProcessStartupFailure() throws Exception {
        String orgName = "org_" + randomString();
        String projectAName = "projectA_" + randomString();
        String projectBName = "projectA_" + randomString();

        ProjectOperationResponse porA = register(orgName, projectAName, "invalidTriggersBrokenProcess/a", 1);
        ProjectOperationResponse porB = register(orgName, projectBName, "invalidTriggersBrokenProcess/b", 1);

        // ---

        String policyName = "policy_" + randomString();

        PolicyApi policyApi = new PolicyApi(getApiClient());
        policyApi.createOrUpdatePolicy(new PolicyEntry()
                .name(policyName)
                .rules(readPolicy("invalidTriggersBrokenProcess/policy.json")));

        policyApi.linkPolicy(policyName, new PolicyLinkEntry().orgName(orgName));

        // ---

        ExternalEventsApi eventResource = new ExternalEventsApi(getApiClient());
        eventResource.externalEvent("testTrigger2", Collections.singletonMap("x", "abc"));

        // ---

        waitForProcs(porA.getId(), 1, StatusEnum.FAILED);
        waitForProcs(porB.getId(), 1, StatusEnum.FINISHED);
    }

    @Test
    public void testTriggerProfiles() throws Exception {
        String orgName = "org_" + randomString();
        String projectName = "project_" + randomString();
        ProjectOperationResponse por = register(orgName, projectName, "triggerActiveProfiles", 1);

        // ---

        ExternalEventsApi eventResource = new ExternalEventsApi(getApiClient());
        eventResource.externalEvent("testTrigger", Collections.emptyMap());

        // ---

        List<ProcessEntry> l = waitForProcs(por.getId(), 1, StatusEnum.FINISHED);
        ProcessEntry pe = l.get(0);

        byte[] ab = getLog(pe.getInstanceId());
        assertLog(".*Hello, Concord.*", ab);
    }

    private ProjectOperationResponse register(String orgName, String projectName, String repoResource, int expectedTriggerCount) throws Exception {
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        if (orgApi.getOrg(orgName) == null) {
            orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
        }

        // ---

        String repoName = "repo_" + randomString();

        ProjectOperationResponse por = createProject(orgName, projectName, repoName, repoResource);

        // ---

        if (expectedTriggerCount < 0) {
            return por;
        }

        TriggersApi triggerResource = new TriggersApi(getApiClient());
        while (true) {
            List<TriggerEntry> triggers = triggerResource.listTriggers(orgName, projectName, repoName);
            if (triggers != null && triggers.size() == expectedTriggerCount) {
                break;
            }

            Thread.sleep(1000);
        }

        return por;
    }

    private ProjectOperationResponse createProject(String orgName, String projectName, String repoName, String repoResource) throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggerIT.class.getResource(repoResource).toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = "file://" + tmpDir.toAbsolutePath().toString();

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        return projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));
    }

    private Map<String, Object> readPolicy(String file) throws Exception {
        URL url = TriggerIT.class.getResource(file);
        return fromJson(new File(url.toURI()));
    }

    private List<ProcessEntry> waitForProcs(UUID projectId, int expectedCount, StatusEnum expectedStatus) throws Exception {
        ProcessV2Api processApi = new ProcessV2Api(getApiClient());

        ProcessListFilter filter = ProcessListFilter.builder()
                .projectId(projectId)
                .limit(10)
                .offset(0)
                .build();

        while (true) {
            List<ProcessEntry> l = processApi.listProcesses(filter);
            if (l != null && l.size() == expectedCount && allHasStatus(l, expectedStatus)) {
                return l;
            }

            Thread.sleep(1000);
        }
    }

    private static boolean allHasStatus(List<ProcessEntry> l, StatusEnum s) {
        if (s == null) {
            return true;
        }
        return l.stream().allMatch(e -> s.equals(e.getStatus()));
    }
}
