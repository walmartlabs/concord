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

import com.walmartlabs.concord.client2.ProcessEntry;
import com.walmartlabs.concord.client2.StartProcessResponse;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.it.common.ITUtils.archive;
import static com.walmartlabs.concord.it.common.ServerClient.assertLog;
import static com.walmartlabs.concord.it.common.ServerClient.waitForCompletion;

public class ProjectTaskIT extends AbstractServerIT {

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

            Path initialData = Paths.get(GitRepositoryIT.class.getResource("gitRepository").toURI());
            IOUtils.copy(initialData, workdir);

            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();

            git.push().call();
        }

        gitServer = new MockGitSshServer(0, bareRepo);
        gitServer.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void testCreate() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + System.currentTimeMillis();
        String repoName = "repo_" + System.currentTimeMillis();
        String repoUrl = String.format(ITConstants.CUSTOM_GIT_SERVER_URL_PATTERN, gitServer.getPort());
        String repoSecret = "secret_" + System.currentTimeMillis();
        generateKeyPair(orgName, repoSecret, false, null);

        // ---

        byte[] payload = archive(ProjectTaskIT.class.getResource("projectTask").toURI());

        Map<String, Object> input = new HashMap<>();
        input.put("archive", payload);

        Map<String, Object> args = new HashMap<>();
        args.put("projectName", projectName);
        args.put("repoName", repoName);
        args.put("repoUrl", repoUrl);
        args.put("repoSecret", repoSecret);

        input.put("request", Collections.singletonMap("arguments", args));

        // ---

        StartProcessResponse spr = start(input);

        ProcessEntry pir = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(pir.getInstanceId());

        assertLog(".*CREATED.*", ab);
        assertLog(".*Done!.*", ab);
    }
}
