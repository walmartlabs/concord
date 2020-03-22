package com.walmartlabs.concord.cli.runner;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.sdk.Context;
import com.walmartlabs.concord.sdk.SecretService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class CliSecretService implements SecretService {

    private final Path secretStoreDir;

    public CliSecretService(Path secretStoreDir) {
        this.secretStoreDir = secretStoreDir;
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String exportAsString(Context ctx, String instanceId, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        return exportKeyAsFile(ctx, instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        Path publicKey = secretStoreDir;
        Path privateKey = secretStoreDir;
        if (orgName != null) {
            publicKey = publicKey.resolve(orgName);
            privateKey = privateKey.resolve(orgName);
        }
        publicKey = publicKey.resolve(name + ".pub");
        privateKey = privateKey.resolve(name);

        if (Files.notExists(publicKey)) {
            throw new RuntimeException("Public key '" + publicKey + "' not found");
        }

        if (Files.notExists(privateKey)) {
            throw new RuntimeException("Prvate key '" + privateKey + "' not found");
        }

        Path dest = Paths.get(workDir).resolve(Constants.Files.CONCORD_TMP_DIR_NAME);
        Path tmpPublicKey = dest.resolve(name + ".pub");
        Path tmpPrivateKey = dest.resolve(name);
        Files.copy(publicKey, tmpPublicKey);
        Files.copy(privateKey, tmpPrivateKey);

        Map<String, String> m = new HashMap<>();
        m.put("private", tmpPrivateKey.toString());
        m.put("public", tmpPublicKey.toString());
        return m;
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Map<String, String> exportCredentials(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String exportAsFile(Context ctx, String instanceId, String workDir, String orgName, String name, String password) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String decryptString(Context ctx, String instanceId, String s) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public String encryptString(Context ctx, String instanceId, String orgName, String projectName, String value) throws Exception {
        throw new UnsupportedOperationException("not implemented");
    }
}
