package com.walmartlabs.concord.it.server;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.it.common.ServerClient;
import com.walmartlabs.concord.server.api.org.secret.SecretOperationResponse;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.junit.After;
import org.junit.Before;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractServerIT {

    private ServerClient serverClient;

    @Before
    public void _init() throws Exception {
        serverClient = new ServerClient(ITConstants.SERVER_URL);
    }

    @After
    public void _destroy() {
        serverClient.close();
    }

    protected StartProcessResponse start(Map<String, Object> input) {
        return serverClient.start(input);
    }

    @Deprecated
    protected StartProcessResponse start(Map<String, Object> input, boolean sync) {
        return serverClient.start(input, sync);
    }

    @Deprecated
    protected StartProcessResponse start(String entryPoint, Map<String, Object> input) {
        return serverClient.start(entryPoint, input, false);
    }

    protected SecretOperationResponse addPlainSecret(String orgName, String name, boolean generatePassword, String storePassword, byte[] secret) {
        return serverClient.addPlainSecret(orgName, name, generatePassword, storePassword, secret);
    }

    protected SecretOperationResponse addUsernamePassword(String orgName, String name, boolean generatePassword, String storePassword, String username, String password) {
        return serverClient.addUsernamePassword(orgName, name, generatePassword, storePassword, username, password);
    }

    protected SecretOperationResponse generateKeyPair(String orgName, String name, boolean generatePassword, String storePassword) {
        return serverClient.generateKeyPair(orgName, name, generatePassword, storePassword);
    }

    protected <T> T proxy(Class<T> klass) {
        return serverClient.proxy(klass);
    }

    protected byte[] getLog(String logFileName) {
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

    protected void waitForLog(String logFileName, String pattern) throws IOException, InterruptedException {
        serverClient.waitForLog(logFileName, pattern);
    }

    protected <T> T request(String uri, Map<String, Object> input, Class<T> entityType) {
        return serverClient.request(uri, input, entityType);
    }

    private static final char[] RANDOM_CHARS = "abcdef0123456789".toCharArray();

    protected String randomString() {
        StringBuilder b = new StringBuilder();
        b.append(System.currentTimeMillis()).append("_");

        Random rng = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            int n = rng.nextInt(RANDOM_CHARS.length);
            b.append(RANDOM_CHARS[n]);
        }

        return b.toString();
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
}
