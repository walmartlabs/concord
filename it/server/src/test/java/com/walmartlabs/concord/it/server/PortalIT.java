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
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PortalIT extends AbstractServerIT {

    private MockGitSshServer gitServer;
    private int gitPort;

    @BeforeEach
    public void setUp() throws Exception {
        Path data = Paths.get(PortalIT.class.getResource("portal").toURI());
        Path repo = GitUtils.createBareRepository(data);

        gitServer = new MockGitSshServer(0, repo);
        gitServer.start();

        gitPort = gitServer.getPort();
    }

    @AfterEach
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void test() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project@" + randomString();
        String repoSecretName = "repoSecret@" + randomString();
        String repoName = "repo@" + randomString();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN, gitPort);

        // ---

        SecretOperationResponse response = generateKeyPair(orgName, repoSecretName, false, null);

        // ---

        RepositoryEntry repo = new RepositoryEntry()
                .name(repoName)
                .url(repoUrl)
                .branch("master")
                .secretId(response.getId());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        ProjectOperationResponse por = projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(singletonMap(repoName, repo)));

        // ---

        ProjectProcessesApi portalService = new ProjectProcessesApi(getApiClient());
        portalService.startProjectProcess(orgName, projectName, repoName, "main", null, null, "test1,test2");

        // ---

        ProcessV2Api processApi = new ProcessV2Api(getApiClient());
        List<ProcessEntry> l = processApi.listProcesses(ProcessListFilter.builder()
                .projectId(por.getId())
                .limit(10)
                .build());
        assertEquals(1, l.size());

        // ---

        ProcessEntry pe = l.get(0);
        pe = waitForCompletion(getApiClient(), pe.getInstanceId());

        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());
    }
}
