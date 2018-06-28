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


import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.InputStream;
import com.walmartlabs.concord.client.SecretOperationResponse;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.it.common.ITUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.junit.Before;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractServerIT {

    private ServerClient serverClient;

    @Before
    public void _init() {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    protected ApiClient getApiClient() {
        return serverClient.getClient();
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
        return serverClient.addPlainSecret(orgName, name, generatePassword, storePassword, secret);
    }

    protected SecretOperationResponse addUsernamePassword(String orgName, String name, boolean generatePassword, String storePassword, String username, String password) throws ApiException {
        return serverClient.addUsernamePassword(orgName, null, name, generatePassword, storePassword, username, password);
    }

    protected SecretOperationResponse addUsernamePassword(String orgName, String projectName, String name, boolean generatePassword, String storePassword, String username, String password) throws ApiException {
        return serverClient.addUsernamePassword(orgName, projectName, name, generatePassword, storePassword, username, password);
    }

    protected SecretOperationResponse generateKeyPair(String orgName, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, name, generatePassword, storePassword);
    }

    protected byte[] getLog(String logFileName) throws ApiException {
        return serverClient.getLog(logFileName);
    }

    protected void resetApiKey() {
        serverClient.resetApiKey();
    }

    protected void setApiKey(String apiKey) {
        serverClient.setApiKey(apiKey);
    }

    protected void setGithubKey(String key) {
        serverClient.setGithubKey(key);
    }

    protected void waitForLog(String logFileName, String pattern) throws IOException, InterruptedException, ApiException {
        serverClient.waitForLog(logFileName, pattern);
    }

    protected <T> T request(String uri, Map<String, Object> input, Class<T> entityType) throws ApiException {
        return serverClient.request(uri, input, entityType);
    }

    protected String randomString() {
        return ITUtils.randomString();
    }

    protected static Path createTempDir() throws IOException {
        Path tmpDir = Files.createTempDirectory("test");
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rwxr-xr-x"));
        return tmpDir;
    }

    protected static Path createTempFile(String suffix) throws IOException {
        Path tmpDir = Files.createTempFile("test", suffix);
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rw-r--r--"));
        return tmpDir;
    }

    protected Map<String, Object> fromJson(File f) throws IOException {
        try (Reader r = new FileReader(f)) {
            return getApiClient().getJSON().getGson().fromJson(r, Map.class);
        }
    }
}
