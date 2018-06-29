package com.walmartlabs.concord.runner;

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

import com.google.gson.reflect.TypeToken;
import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ApiClientFactory;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.SecretEntry;
import com.walmartlabs.concord.client.SecretsApi;
import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.SecretService;

import javax.inject.Inject;
import javax.inject.Named;
import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Named
public class SecretServiceImpl implements SecretService {

    private static final int RETRY_COUNT = 3;

    private static final long RETRY_INTERVAL = 5000;

    private final ApiClientFactory clientFactory;

    @Inject
    public SecretServiceImpl(ApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String name, String password) throws Exception {
        return exportAsString(ctx, instanceId, null, name, password);
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String orgName, String name, String password) throws Exception {
        BinaryDataSecret s = get(ctx, orgName, name, password, SecretEntry.TypeEnum.DATA);
        return new String(s.getData());
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportKeyAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        KeyPair kp = get(ctx, orgName, name, password, SecretEntry.TypeEnum.KEY_PAIR);

        Path baseDir = Paths.get(workDir);
        Path tmpDir = assertTempDir(baseDir);

        Path privateKey = Files.createTempFile(tmpDir, "private", ".key");
        Files.write(privateKey, kp.getPrivateKey());

        Path publicKey = Files.createTempFile(tmpDir, "public", ".key");
        Files.write(publicKey, kp.getPublicKey());

        Map<String, String> m = new HashMap<>();
        m.put("private", baseDir.relativize(privateKey).toString());
        m.put("public", baseDir.relativize(publicKey).toString());

        return m;
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportCredentials(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        UsernamePassword up = get(ctx, orgName, name, password, SecretEntry.TypeEnum.USERNAME_PASSWORD);

        Map<String, String> m = new HashMap<>();
        m.put("username", up.getUsername());
        m.put("password", new String(up.getPassword()));
        return m;
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        BinaryDataSecret bds = get(ctx, orgName, name, password, SecretEntry.TypeEnum.DATA);

        Path baseDir = Paths.get(workDir);
        Path tmpDir = assertTempDir(baseDir);

        Path p = Files.createTempFile(tmpDir, "file", ".bin");
        Files.write(p, bds.getData());

        return baseDir.relativize(p).toString();
    }

    @Override
    public String decryptString(Context ctx, String instanceId, String s) throws Exception {
        byte[] input = DatatypeConverter.parseBase64Binary(s);

        ApiClient c = clientFactory.create(ctx);

        String path = "/api/v1/process/" + instanceId + "/decrypt";
        ApiResponse<byte[]> r = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, () -> {
            Type returnType = new TypeToken<byte[]>() {
            }.getType();
            return ClientUtils.postData(c, path, input, returnType);
        });

        return new String(r.getData());
    }

    private <T> T get(Context ctx, String orgName, String secretName, String password, SecretEntry.TypeEnum type) throws Exception {
        Map<String, Object> params = new HashMap<>();
        String pwd = password;
        if (password == null) {
            pwd = "";
        }
        params.put("storePassword", pwd);

        SecretsApi api = new SecretsApi(clientFactory.create(ctx));

        ApiResponse<File> r = null;
        try {
            String path = "/api/v1/org/" + assertOrgName(ctx, orgName) + "/secret/" + secretName + "/data";

            r = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL,
                    () -> ClientUtils.postData(api.getApiClient(), path, params, File.class));
            if (r.getData() == null) {
                throw new IllegalArgumentException("Secret not found: " + secretName);
            }

            assertSecretType(type, r);

            return readSecret(type, Files.readAllBytes(r.getData().toPath()));
        } finally {
            if (r != null && r.getData() != null) {
                Files.delete(r.getData().toPath());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String assertOrgName(Context ctx, String orgName) {
        if (orgName != null) {
            return orgName;
        }

        Map<String, Object> pi = (Map<String, Object>) ctx.getVariable(Constants.Request.PROJECT_INFO_KEY);
        return Optional.ofNullable(pi)
                .map(p -> (String)p.get("orgName"))
                .orElseThrow(() -> new IllegalArgumentException("Organization name not specified"));
    }

    @SuppressWarnings("unchecked")
    private static <T> T readSecret(SecretEntry.TypeEnum type, byte[] bytes) {
        switch (type) {
            case DATA:
                return (T) new BinaryDataSecret(bytes);
            case KEY_PAIR:
                return (T) KeyPair.deserialize(bytes);
            case USERNAME_PASSWORD:
                return (T) UsernamePassword.deserialize(bytes);
            default:
                throw new IllegalArgumentException("unknown secret type: " + type);
        }
    }

    private static Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(".tmp");
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }

    private static void assertSecretType(SecretEntry.TypeEnum expected, ApiResponse<File> response) {
        SecretEntry.TypeEnum actual = SecretEntry.TypeEnum.valueOf(ClientUtils.getHeader(InternalConstants.Headers.SECRET_TYPE, response));
        if (expected != actual) {
            throw new IllegalArgumentException("Expected " + expected + " got " + actual);
        }
    }
}
