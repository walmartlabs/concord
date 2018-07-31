package com.walmartlabs.concord.it.webhook;

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

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.*;
import com.walmartlabs.concord.it.common.GitUtils;
import com.walmartlabs.concord.it.common.MockGitSshServer;
import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.walmartlabs.concord.it.common.ITUtils.randomString;
import static java.util.Collections.singletonMap;

public class GithubWebhookIT {

    private static MockGitSshServer gitServer;
    private static int gitPort;

    private ServerClient serverClient;

    @ClassRule
    public static WireMockClassRule wireMockClassRule = new WireMockClassRule(WireMockConfiguration.options()
            .port(ITConstants.GIT_WEBHOOK_MOCK_PORT));

    @Rule
    public WireMockClassRule rule = wireMockClassRule;


    @BeforeClass
    public static void preCondition() {
        Assume.assumeTrue(Boolean.valueOf(System.getenv("IT_WEBHOOK_TRIGGER_ENABLED")));
    }

    @Before
    public void setUp() throws Exception {
        serverClient = new ServerClient(ITConstants.SERVER_URL);

        Path data = Paths.get(GithubWebhookIT.class.getResource("githubWebHook").toURI());
        Path repo = GitUtils.createBareRepository(data);

        gitServer = new MockGitSshServer(0, repo.toAbsolutePath().toString());
        gitServer.start();

        gitPort = gitServer.getPort();

        stubForGetRepo();

        stubForGitWebHook();

        stubForGetGitUser();
    }

    @After
    public void tearDown() throws Exception {
        gitServer.stop();
    }

    @Test
    public void testGithubWebHookForCreateOrUpdateProject() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();
        String repoSecretName = "repoSecret_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN + "testUser/project1", gitPort);

        // ---

        generateKeyPair(orgName, repoSecretName, false, null);

        // ---

        RepositoryEntry repo = new RepositoryEntry()
                .setName(repoName)
                .setUrl(repoUrl)
                .setBranch("master")
                .setSecretName(repoSecretName);
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(singletonMap(repoName, repo))
                .setCfg(Collections.emptyMap()));

        verify(postRequestedFor(urlEqualTo("/repos/testUser/project1/hooks")));

        // ---

        repo.setName(repoName)
                .setUrl(repoUrl)
                .setBranch("featureBranch")
                .setSecretName(repoSecretName);

        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setRepositories(singletonMap(repoName, repo))
                .setCfg(Collections.emptyMap()));

        verify(postRequestedFor(urlEqualTo("/repos/testUser/project1/hooks")));
    }

    @Test
    public void testGithubWebHookForCreateOrUpdateRepository() throws Exception {
        String orgName = "Default";

        // ---

        String projectName = "project_" + randomString();
        String repoSecretName = "repoSecret_" + randomString();
        String repoName = "repo_" + randomString();
        String repoUrl = String.format(ITConstants.GIT_SERVER_URL_PATTERN + "testUser/project1", gitPort);

        // ---

        generateKeyPair(orgName, repoSecretName, false, null);

        // ---

        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        projectsApi.createOrUpdate(orgName, new ProjectEntry()
                .setName(projectName)
                .setCfg(Collections.emptyMap()));

        RepositoryEntry repo = new RepositoryEntry()
                .setName(repoName)
                .setUrl(repoUrl)
                .setBranch("master")
                .setSecretName(repoSecretName);

        RepositoriesApi repositoriesApi = new RepositoriesApi(getApiClient());
        repositoriesApi.createOrUpdate(orgName, projectName, repo);

        verify(postRequestedFor(urlEqualTo("/repos/testUser/project1/hooks")));

        // ---

        repo = new RepositoryEntry()
                .setName(repoName)
                .setUrl(repoUrl)
                .setBranch("featureBranch")
                .setSecretName(repoSecretName);

        repositoriesApi.createOrUpdate(orgName, projectName, repo);

        verify(postRequestedFor(urlEqualTo("/repos/testUser/project1/hooks")));
    }

    private void stubForGetGitUser() {
        rule.stubFor(get(urlEqualTo("/user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"login\": \"testUser\"}"))
        );
    }

    private void stubForGetRepo() {
        rule.stubFor(get(urlEqualTo("/repos/testUser/project1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                " \"name\": \"project1\", \n" +
                                "    \"owner\": {\n" +
                                "      \"login\": \"testUser\"},\n" +
                                "    \"git_url\": \"git:localhost/testUser/project1.git\"\n" +
                                "}"))
        );
    }

    private void stubForGitWebHook() {
        rule.stubFor(post(urlEqualTo("/repos/testUser/project1/hooks"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{\n" +
                                "  \"id\": 1\n" +
                                "}"))
        );
    }

    private SecretOperationResponse generateKeyPair(String orgName, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, name, generatePassword, storePassword);
    }

    private ApiClient getApiClient() {
        return serverClient.getClient();
    }
}
