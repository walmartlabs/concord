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
import com.walmartlabs.concord.common.PathUtils;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GeneralTriggerV2IT extends AbstractGeneralTriggerIT {

    private String orgName;
    private String projectName;
    private String repoName;
    private OrganizationsApi orgApi;

    private void setup(String yamlPath) throws Exception {
        Path tmpDir = createSharedTempDir();

        File src = new File(TriggersRefreshIT.class.getResource(yamlPath).toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        String gitUrl = tmpDir.toAbsolutePath().toString();

        // ---

        orgName = "org_" + randomString();
        projectName = "project_" + randomString();
        repoName = "repo_" + randomString();

        orgApi = new OrganizationsApi(getApiClient());
        orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .visibility(ProjectEntry.VisibilityEnum.PUBLIC)
                .repositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .url(gitUrl)
                        .branch("master"))));
    }

    private void cleanup() throws ApiException {
        orgApi.deleteOrg(orgName, "yes");
    }

    @Test
    public void testExclusiveV2() throws Exception {
        setup("generalExclusiveTriggerv2");

        waitForTriggers(orgName, projectName, repoName, 2);

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'RED' is already in the queue. Current process has been cancelled.*");

        cleanup();
    }

    @Test
    public void testExclusiveFromConfigurationV2() throws Exception {
        setup("generalTriggerWithExclusiveCfgv2");

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'RED' is already in the queue. Current process has been cancelled.*");

        // ---

        cleanup();
    }

    @Test
    public void testExclusiveWithTriggerOverrideV2() throws Exception {
        setup("generalTriggerWithExclusiveOverridev2");

        waitForTriggers(orgName, projectName, repoName, 2);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", "value2");

        // first process
        eea.externalEvent("testTriggerv2", eventParam);

        // second process
        eea.externalEvent("testTriggerv2", eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*Hello from exclusive trigger v2.*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED), ".*Process\\(es\\) with exclusive group 'TRIGGER' is already in the queue. Current process has been cancelled.*");

        // ---

        cleanup();
    }
}
