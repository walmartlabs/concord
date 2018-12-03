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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Collections.singletonMap;

public class PortalIT extends AbstractServerIT {

    private MockGitSshServer gitServer;
    private int gitPort;

    @Before
    public void setUp() throws Exception {
        Path data = Paths.get(PortalIT.class.getResource("portal").toURI());
        Path repo = GitUtils.createBareRepository(data);

        gitServer = new MockGitSshServer(0, repo.toAbsolutePath().toString());
        gitServer.start();

        gitPort = gitServer.getPort();
    }

    @After
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
                .setName(repoName)
                .setUrl(repoUrl)
                .setBranch("master")
                .setSecretId(response.getId());
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(singletonMap(repoName, repo)));

        // ---

        ProjectProcessesApi portalService = new ProjectProcessesApi(getApiClient());
        portalService.start(orgName, projectName, repoName, "main", "test1,test2", null, null);
    }
}
