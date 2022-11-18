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

import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GitBranchesIT extends AbstractServerIT {

    private MockGitSshServer gitServer;

    @BeforeEach
    public void setUp() throws Exception {
        Path bareRepo = createTempDir();
        try (Git git = Git.init().setInitialBranch("master").setBare(true).setDirectory(bareRepo.toFile()).call()) {
        }

        Path workdir = createTempDir();
        try (Git git = Git.cloneRepository()
                .setDirectory(workdir.toFile())
                .setURI("file://" + bareRepo)
                .call()) {

            Path initialData = Paths.get(PortalIT.class.getResource("gitBranches/qa").toURI());
            IOUtils.copy(initialData, workdir);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();

            git.checkout().setCreateBranch(true).setName("qa").call();
            git.push().setRefSpecs(new RefSpec("qa:qa")).call();

            git.checkout().setCreateBranch(true).setName("dev").call();

            Path devData = Paths.get(PortalIT.class.getResource("gitBranches/dev").toURI());
            IOUtils.copy(devData, workdir, StandardCopyOption.REPLACE_EXISTING);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("dev commit").call();
            git.push().setRefSpecs(new RefSpec("dev:dev")).call();
        }

        gitServer = new MockGitSshServer(0, bareRepo);
        gitServer.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void test() throws Exception {
        String orgName = "Default";
        String projectName = "project_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN, gitServer.getPort());
        String repoSecret = "secret_" + randomString();

        SecretOperationResponse sor = generateKeyPair(orgName, repoSecret, false, null);
        assertEquals(SecretOperationResponse.ResultEnum.CREATED, sor.getResult());

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(Collections.singletonMap(repoName, new RepositoryEntry()
                        .setUrl(repoUrl)
                        .setSecretId(sor.getId())
                        .setBranch("qa"))));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessApi processApi = new ProcessApi(getApiClient());
        ProcessEntry pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        byte[] ab = getLog(pe.getLogFileName());
        assertLog(".*running qa.*", ab);

        // ---

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        repositoriesApi.createOrUpdate(orgName, projectName, new RepositoryEntry()
                .setName(repoName)
                .setUrl(repoUrl)
                .setSecretId(sor.getId())
                .setBranch("dev"));

        // ---

        input = new HashMap<>();
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        spr = start(input);

        // ---

        pe = waitForCompletion(processApi, spr.getInstanceId());
        assertEquals(ProcessEntry.StatusEnum.FINISHED, pe.getStatus());

        ab = getLog(pe.getLogFileName());
        assertLog(".*running dev.*", ab);
    }
}
