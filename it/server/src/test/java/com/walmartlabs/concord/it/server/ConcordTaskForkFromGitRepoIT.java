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

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConcordTaskForkFromGitRepoIT extends AbstractServerIT {

    private MockGitSshServer gitServer;
    private int gitPort;

    @BeforeEach
    public void setUp() throws Exception {
        Path data = Paths.get(PortalIT.class.getResource("concordTaskFork").toURI());
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
    public void testFork() throws Exception {
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
                .setName(repoName)
                .setUrl(repoUrl)
                .setBranch("master")
                .setSecretId(response.getId());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(singletonMap(repoName, repo)));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        StartProcessResponse parentSpr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry parentProcessEntry = waitForCompletion(processApi, parentSpr.getInstanceId());

        byte[] ab = getLog(parentProcessEntry.getLogFileName());
        assertLog(".*repoCommitMessage: initial message.*", ab);

        ProcessEntry processEntry = processApi.get(parentSpr.getInstanceId());
        assertEquals(1, processEntry.getChildrenIds().size());

        ProcessEntry child = processApi.get(processEntry.getChildrenIds().get(0));
        assertNotNull(child);
        assertEquals(ProcessEntry.StatusEnum.FINISHED, child.getStatus());

        ab = getLog(child.getLogFileName());
        assertLog(".*repoCommitMessage: initial message.*", ab);
    }
}
