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
import com.walmartlabs.concord.client.SecretOperationResponse;
import com.walmartlabs.concord.client.StartProcessResponse;
import com.walmartlabs.concord.it.common.ITUtils;
import com.walmartlabs.concord.it.common.ServerClient;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.it.server.AbstractServerIT.DEFAULT_TEST_TIMEOUT;

@Timeout(value = DEFAULT_TEST_TIMEOUT, unit = TimeUnit.MILLISECONDS)
public abstract class AbstractServerIT {

    public static final long DEFAULT_TEST_TIMEOUT = 120000;

    private ServerClient serverClient;

    @BeforeAll
    public static void _initJGit() {
        SystemReader.setInstance(new JGitSystemReader());
    }

    @BeforeEach
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
        return serverClient.generateKeyPair(orgName, null, name, generatePassword, storePassword);
    }

    protected SecretOperationResponse generateKeyPair(String orgName, String projectName, String name, boolean generatePassword, String storePassword) throws ApiException {
        return serverClient.generateKeyPair(orgName, projectName, name, generatePassword, storePassword);
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

    protected static String randomString() {
        return ITUtils.randomString();
    }

    protected static String randomString(int length) {
        return ITUtils.randomString(length);
    }

    protected static String randomPwd() {
        return ITUtils.randomPwd();
    }

    protected static Path createTempDir() throws IOException {
        return ITUtils.createTempDir();
    }

    protected static Path createTempFile(String suffix) throws IOException {
        Path tmpDir = Files.createTempFile("test", suffix);
        Files.setPosixFilePermissions(tmpDir, PosixFilePermissions.fromString("rw-r--r--"));
        return tmpDir;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> fromJson(File f) throws IOException {
        try (Reader r = new FileReader(f)) {
            return getApiClient().getJSON().getGson().fromJson(r, Map.class);
        }
    }

    protected static String env(String k, String def) {
        String v = System.getenv(k);
        if (v == null) {
            return def;
        }
        return v;
    }

    private static class JGitSystemReader extends SystemReader {

        private static class EmptyFileBasedConfig extends FileBasedConfig {

            public EmptyFileBasedConfig(Config parent, FS fs) {
                super(parent, null, fs);
            }

            @Override
            public void load() {
                // empty, do not load
            }

            @Override
            public boolean isOutdated() {
                return false;
            }
        }

        private volatile String hostname;

        @Override
        public String getenv(String variable) {
            return System.getenv(variable);
        }

        @Override
        public String getProperty(String key) {
            return System.getProperty(key);
        }

        @Override
        public FileBasedConfig openSystemConfig(Config parent, FS fs) {
            return new EmptyFileBasedConfig(parent, fs);
        }

        @Override
        public FileBasedConfig openUserConfig(Config parent, FS fs) {
            return new EmptyFileBasedConfig(parent, fs);
        }

        @Override
        public FileBasedConfig openJGitConfig(Config parent, FS fs) {
            return new EmptyFileBasedConfig(parent, fs);
        }

        @Override
        public String getHostname() {
            if (hostname == null) {
                try {
                    InetAddress localMachine = InetAddress.getLocalHost();
                    hostname = localMachine.getCanonicalHostName();
                } catch (UnknownHostException e) {
                    hostname = "localhost";
                }
                assert hostname != null;
            }
            return hostname;
        }

        @Override
        public long getCurrentTime() {
            return System.currentTimeMillis();
        }

        @Override
        public int getTimezone(long when) {
            return getTimeZone().getOffset(when) / (60 * 1000);
        }
    }
}
