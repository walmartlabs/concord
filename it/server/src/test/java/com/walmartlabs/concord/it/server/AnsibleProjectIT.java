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
import com.walmartlabs.concord.it.common.ITConstants;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import com.walmartlabs.concord.sdk.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static com.walmartlabs.concord.it.common.ServerClient.*;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @see "docs/examples/ansible_project"
 */
public class AnsibleProjectIT extends AbstractServerIT {

    private MockGitSshServer gitServer;
    private int gitPort;

    @BeforeEach
    public void setUp() throws Exception {
        Path data = Paths.get(AnsibleProjectIT.class.getResource("ansibleproject/git").toURI());
        Path repo = GitUtils.createBareRepository(data, ConcordConfiguration.sharedDir());

        gitServer = new MockGitSshServer(0, repo);
        gitServer.start();

        gitPort = gitServer.getPort();
        org.testcontainers.Testcontainers.exposeHostPorts(gitPort);
    }

    @AfterEach
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void test() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("request", resource("ansibleproject/request.json"));
        input.put("inventory", resource("ansibleproject/inventory.ini"));
        test(input);
    }

    @Test
    public void testInlineInventory() throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put("request", resource("ansibleproject/requestInline.json"));
        test(input);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFailure() throws Exception {
        String orgName = "Default";

        // ---

        String templatePath = "file://" + ITConstants.DEPENDENCIES_DIR + "/ansible-template.jar";

        String projectName = "project_" + randomString();
        String repoSecretName = "repoSecret_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = String.format("ssh://git@" + concord().hostAddressAccessibleByContainers() + ":%d/", gitPort);

        // ---

        generateKeyPair(orgName, repoSecretName, false, null);

        // ---

        RepositoryEntry repo = new RepositoryEntry()
                .name(repoName)
                .url(repoUrl)
                .branch("master")
                .secretName(repoSecretName);
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = Collections.singletonMap(Constants.Request.TEMPLATE_KEY, templatePath);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(singletonMap(repoName, repo))
                .cfg(cfg)
                .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));

        // ---

        Map<String, Object> input = new HashMap<>();
        input.put("request", resource("ansibleproject/requestFailure.json"));
        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        StartProcessResponse spr = start(input);

        // ---

        waitForStatus(getApiClient(), spr.getInstanceId(), ProcessEntry.StatusEnum.FAILED);

        // ---

        try (InputStream resp = downloadAttachment(spr.getInstanceId(), "ansible_stats.json")) {
            assertNotNull(resp);

            Map<String, Object> stats = fromJson(resp);
            Collection<String> failures = (Collection<String>) stats.get("failures");
            assertNotNull(failures);
            assertEquals(1, failures.size());
            assertEquals("128.0.0.1", failures.iterator().next());
        }
    }

    @SuppressWarnings("unchecked")
    public void test(Map<String, Object> input) throws Exception {
        String orgName = "Default";

        // ---

        String templatePath = "file://" + ITConstants.DEPENDENCIES_DIR + "/ansible-template.jar";

        String projectName = "project_" + randomString();
        String repoSecretName = "repoSecret_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = String.format("ssh://git@" + concord().hostAddressAccessibleByContainers() + ":%d/", gitPort);

        // ---

        generateKeyPair(orgName, repoSecretName, false, null);

        // ---

        RepositoryEntry repo = new RepositoryEntry()
                .name(repoName)
                .url(repoUrl)
                .branch("master")
                .secretName(repoSecretName);
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        Map<String, Object> cfg = Collections.singletonMap(Constants.Request.TEMPLATE_KEY, templatePath);
        projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                .name(projectName)
                .repositories(singletonMap(repoName, repo))
                .cfg(cfg));

        // ---

        input.put("org", orgName);
        input.put("project", projectName);
        input.put("repo", repoName);
        StartProcessResponse spr = start(input);

        // ---

        ProcessEntry psr = waitForCompletion(getApiClient(), spr.getInstanceId());

        // ---

        byte[] ab = getLog(psr.getInstanceId());
        assertLog(".*\"msg\":.*Hello, world.*", ab);

        // check if `force_color` is working
        assertLogAtLeast(".*\\[0;32m.*", 3, ab);

        // ---

        try (InputStream resp = downloadAttachment(spr.getInstanceId(), "ansible_stats.json")) {
            assertNotNull(resp);
            Map<String, Object> stats = fromJson(resp);

            Collection<String> oks = (Collection<String>) stats.get("ok");
            assertNotNull(oks);
            assertEquals(1, oks.size());
            assertEquals("127.0.0.1", oks.iterator().next());
        }
    }

    private static InputStream resource(String path) {
        return AnsibleProjectIT.class.getResourceAsStream(path);
    }

    private InputStream downloadAttachment(UUID instanceId, String name) throws InterruptedException, ApiException {
        int attemptsLeft = 3;
        while (true) {
            try {
                return new ProcessApi(getApiClient()).downloadAttachment(instanceId, name);
            } catch (ApiException e) {
                if (attemptsLeft-- > 0 && (e.getCode() == 404 || e.getCode() >= 500)) {
                    Thread.sleep(500);
                } else {
                    throw e;
                }
            }
        }
    }
}
