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

import ca.ibodrov.concord.testcontainers.junit5.ConcordRule;
import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.common.PathUtils;
import com.walmartlabs.concord.it.common.ITUtils;
import com.walmartlabs.concord.it.common.JGitUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import com.walmartlabs.concord.it.common.SharedConcordExtension;
import org.eclipse.jgit.api.Git;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

public abstract class AbstractServerIT {

    @RegisterExtension
    static SharedConcordExtension concordExtension = new SharedConcordExtension(ConcordConfiguration::configure);

    private static ConcordRule concord;
    private ServerClient serverClient;

    @BeforeAll
    public static void _initJGit(ConcordRule rule) {
        JGitUtils.applyWorkarounds();
        concord = rule;
    }

    @BeforeEach
    public void _init() {
        serverClient = new ServerClient(concord.apiBaseUrl(), concord.environment().apiToken());
    }

    protected static ConcordRule concord() {
        return concord;
    }

    protected static String defaultApiKey() {
        return concord.environment().apiToken();
    }

    protected ApiClient getApiClient() {
        return serverClient.getClient();
    }

    protected ApiClient getApiClientForKey(String apiKey) {
        return serverClient.getClientForApiKey(apiKey);
    }

    protected StartProcessResponse start(String orgName, String projectName, String repoName, String entryPoint, byte[] payload) throws ApiException {
        Map<String, Object> input = new HashMap<>();
        if (orgName != null) {
            input.put("org", orgName);
        }
        if (projectName != null) {
            input.put("project", projectName);
        }
        if (repoName != null) {
            input.put("repo", repoName);
        }
        if (entryPoint != null) {
            input.put("entryPoint", entryPoint);
        }
        if (payload != null) {
            input.put("archive", payload);
        }
        return start(input);
    }

    protected StartProcessResponse start(String entryPoint, byte[] payload) throws ApiException {
        Map<String, Object> input = new HashMap<>();
        input.put("entryPoint", entryPoint);
        input.put("archive", payload);
        return start(input);
    }

    protected StartProcessResponse start(String entryPoint) throws ApiException {
        Map<String, Object> input = new HashMap<>();
        input.put("entryPoint", entryPoint);
        return start(input);
    }

    protected StartProcessResponse start(byte[] payload) throws ApiException {
        return start(Collections.singletonMap("archive", payload));
    }

    protected StartProcessResponse start(InputStream in) throws ApiException {
        return start(Collections.singletonMap("archive", in));
    }

    protected StartProcessResponse start(Map<String, Object> input) throws ApiException {
        return serverClient.start(input);
    }

    protected SecretOperationResponse addPlainSecret(String orgName, String name, boolean generatePassword, String storePassword, byte[] secret) throws ApiException {
        return serverClient.addPlainSecret(orgName, name, null, generatePassword, storePassword, secret);
    }

    protected SecretOperationResponse addPlainSecretWithProjectNames(String orgName, String name, Set<String> projectNames, boolean generatePassword, String storePassword, byte[] secret) throws ApiException {
        return serverClient.addPlainSecret(orgName, name, projectNames, null, generatePassword, storePassword, secret);
    }

    protected SecretOperationResponse addUsernamePassword(String orgName, String name, boolean generatePassword, String storePassword, String username, String password) throws ApiException {
        return serverClient.addUsernamePassword(orgName, null, name, generatePassword, storePassword, username, password);
    }

    protected SecretOperationResponse addUsernamePassword(String orgName, String projectName, String name, boolean generatePassword, String storePassword, String username, String password) throws ApiException {
        return serverClient.addUsernamePassword(orgName, projectName, name, generatePassword, storePassword, username, password);
    }

    protected SecretOperationResponse generateKeyPair(String orgName, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, null, name, generatePassword, storePassword);
    }

    protected SecretOperationResponse generateKeyPair(String orgName, String projectName, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, projectName, name, generatePassword, storePassword);
    }

    protected SecretOperationResponse generateKeyPairWithProjectNames(String orgName, Set<String> projectNames, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, projectNames, null, name, generatePassword, storePassword);
    }

    protected SecretOperationResponse generateKeyPairWithProjectIds(String orgName, Set<UUID> projectIds, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, null, projectIds, name, generatePassword, storePassword);
    }

    protected byte[] getLog(UUID instanceId) throws ApiException {
        return serverClient.getLog(instanceId);
    }

    protected void resetApiKey() {
        serverClient.setApiKey(concord.environment().apiToken());
    }

    protected void setApiKey(String apiKey) {
        serverClient.setApiKey(apiKey);
    }

    protected void setGithubKey(String key) {
        serverClient.setGithubKey(key);
    }

    protected void waitForLog(UUID instanceId, @Language("RegExp") String pattern) throws IOException, InterruptedException, ApiException {
        serverClient.waitForLog(instanceId, pattern);
    }

    protected void waitForLog(UUID instanceId, int retries, @Language("RegExp") String pattern) throws IOException, InterruptedException, ApiException {
        serverClient.waitForLog(instanceId, retries, pattern);
    }

    protected static String randomString() {
        return ITUtils.randomString();
    }

    protected static String randomPwd() {
        return ITUtils.randomPwd();
    }

    protected static Path createSharedTempDir() throws IOException {
        Path dir = Files.createTempDirectory(ConcordConfiguration.sharedDir(), "test");
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return dir;
    }

    protected static Path createSharedTempFile(String suffix) throws IOException {
        Path tmp = Files.createTempFile(ConcordConfiguration.sharedDir(), "test", suffix);
        Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-r--r--"));
        return tmp;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fromJson(File f) throws IOException {
        return fromJson(f, Map.class);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fromJson(InputStream is) throws IOException {
        return getApiClient().getObjectMapper().readValue(is, Map.class);
    }

    protected <T> T fromJson(File f, Class<T> classOfT) throws IOException {
        return getApiClient().getObjectMapper().readValue(f, classOfT);
    }

    protected <T> T fromJson(InputStream is, Class<T> classOfT) throws IOException {
        return getApiClient().getObjectMapper().readValue(is, classOfT);
    }

    protected String createRepo(String resource) throws Exception {
        Path tmpDir = createSharedTempDir();

        File src = new File(AbstractServerIT.class.getResource(resource).toURI());
        PathUtils.copy(src.toPath(), tmpDir);

        try (Git repo = Git.init().setInitialBranch("master").setDirectory(tmpDir.toFile()).call()) {
            repo.add().addFilepattern(".").call();
            repo.commit().setMessage("import").call();
        }

        return tmpDir.toAbsolutePath().toString();
    }

    protected static String env(String k, String def) {
        String v = System.getenv(k);
        if (v == null) {
            return def;
        }
        return v;
    }

    protected void withOrg(Consumer<String> consumer) throws Exception {
        String orgName = "org_" + randomString();
        OrganizationsApi orgApi = new OrganizationsApi(getApiClient());
        try {
            orgApi.createOrUpdateOrg(new OrganizationEntry().name(orgName));
            consumer.accept(orgName);
        } finally {
            orgApi.deleteOrg(orgName, "yes");
        }
    }

    protected void withProject(String orgName, Consumer<String> consumer) throws Exception {
        String projectName = "project_" + randomString();
        ProjectsApi projectsApi = new ProjectsApi(getApiClient());
        try {
            projectsApi.createOrUpdateProject(orgName, new ProjectEntry()
                    .name(projectName)
                    .rawPayloadMode(ProjectEntry.RawPayloadModeEnum.EVERYONE));
            consumer.accept(projectName);
        } finally {
            projectsApi.deleteProject(orgName, projectName);
        }
    }

    @FunctionalInterface
    public interface Consumer<T> {
        void accept(T t) throws Exception;
    }
}
