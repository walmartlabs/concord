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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class GeneralTriggerIT extends AbstractGeneralTriggerIT {

    private String orgName;
    private String projectName;
    private String repoName;
    private OrganizationsApi orgApi;

    private void setup(String fixture) throws Exception {
        Path tmpDir = createTempDir();

        File src = new File(TriggersRefreshIT.class.getResource(fixture).toURI());
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

    @SuppressWarnings("unused")
    private static Stream<Arguments> scenarios() {
        return Stream.of(
                // concord.yml triggers, default (v1) runtime
                Arguments.of("v1: exclusive group in the trigger",
                        "generalExclusiveTrigger", "testTrigger", "value1", 1,
                        "Hello from exclusive trigger!", "RED"),
                Arguments.of("v1: exclusive group in the configuration",
                        "generalTriggerWithExclusiveCfg", "testTrigger", "value1", 1,
                        "Hello from exclusive trigger!", "RED"),
                Arguments.of("v1: trigger-level override of the configuration",
                        "generalTriggerWithExclusiveOverride", "testTrigger", "value1", 1,
                        "Hello from exclusive trigger!", "TRIGGER"),

                // version: 2 triggers
                Arguments.of("v2: exclusive group in the trigger",
                        "generalExclusiveTriggerv2", "testTriggerv2", "value2", 2,
                        "Hello from exclusive trigger v2!", "RED"),
                Arguments.of("v2: exclusive group in the configuration",
                        "generalTriggerWithExclusiveCfgv2", "testTriggerv2", "value2", 2,
                        "Hello from exclusive trigger v2!", "RED"),
                Arguments.of("v2: trigger-level override of the configuration",
                        "generalTriggerWithExclusiveOverridev2", "testTriggerv2", "value2", 2,
                        "Hello from exclusive trigger v2!", "TRIGGER"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    public void testExclusive(String displayName, String fixture, String event, String conditionsValue,
                              int expectedTriggers, String successLog, String exclusiveGroup) throws Exception {
        setup(fixture);

        // ---

        waitForTriggers(orgName, projectName, repoName, expectedTriggers);

        // ---

        ExternalEventsApi eea = new ExternalEventsApi(getApiClient());
        Map<String, Object> eventParam = new HashMap<>();
        eventParam.put("key1", conditionsValue);

        // first process
        eea.externalEvent(event, eventParam);

        // second process
        // we assume that the first process is in the RUNNING status when the second process is created
        eea.externalEvent(event, eventParam);

        Map<ProcessEntry.StatusEnum, ProcessEntry> ps = waitProcesses(orgName, projectName, ProcessEntry.StatusEnum.FINISHED, ProcessEntry.StatusEnum.CANCELLED);
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.FINISHED), ".*" + successLog + ".*");
        assertProcessLog(ps.get(ProcessEntry.StatusEnum.CANCELLED),
                ".*Process\\(es\\) with exclusive group '" + exclusiveGroup + "' is already in the queue. Current process has been cancelled.*");

        // ---

        cleanup();
    }
}
