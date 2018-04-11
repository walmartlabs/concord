package com.walmartlabs.concord.plugins.crypto;

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

import com.walmartlabs.concord.common.secret.BinaryDataSecret;
import com.walmartlabs.concord.common.secret.KeyPair;
import com.walmartlabs.concord.common.secret.UsernamePassword;
import com.walmartlabs.concord.sdk.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Named("crypto")
public class CryptoTask implements Task, SecretReader, SecretStore {

    private static final Logger log = LoggerFactory.getLogger(CryptoTask.class);

    private final SecretReaderService secretReaderService;

    @Inject
    public CryptoTask(RpcClient rpcClient) {
        this.secretReaderService = rpcClient.getSecretReaderService();
    }

    @Override
    public String exportAsString(@InjectVariable("txId") String instanceId,
                                 String name,
                                 String password) throws Exception {

        return exportAsString(instanceId, null, name, password);
    }

    @Override
    public String exportAsString(@InjectVariable("txId") String instanceId,
                                 String orgName,
                                 String name,
                                 String password) throws Exception {

        Secret s = get(instanceId, orgName, name, password);

        if (!(s instanceof BinaryDataSecret)) {
            throw new IllegalArgumentException("The secret '" + name + "'can't be exported as a string");
        }

        BinaryDataSecret bds = (BinaryDataSecret) s;
        return new String(bds.getData());
    }

    @Override
    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String name,
                                               String password) throws Exception {

        return exportKeyAsFile(instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String orgName,
                                               String name,
                                               String password) throws Exception {

        log.info("Exporting a key pair: {}", name);

        Secret s = get(instanceId, orgName, name, password);
        if (!(s instanceof KeyPair)) {
            throw new IllegalArgumentException("Expected a key pair");
        }

        KeyPair kp = (KeyPair) s;

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
    public Map<String, String> exportCredentials(@InjectVariable("txId") String instanceId,
                                                 @InjectVariable("workDir") String workDir,
                                                 String name,
                                                 String password) throws Exception {

        return exportCredentials(instanceId, workDir, null, name, password);
    }

    @Override
    public Map<String, String> exportCredentials(@InjectVariable("txId") String instanceId,
                                                 @InjectVariable("workDir") String workDir,
                                                 String orgName,
                                                 String name,
                                                 String password) throws Exception {

        Secret s = get(instanceId, orgName, name, password);
        if (!(s instanceof UsernamePassword)) {
            throw new IllegalArgumentException("Expected a credentials secret");
        }

        UsernamePassword up = (UsernamePassword) s;

        Map<String, String> m = new HashMap<>();
        m.put("username", up.getUsername());
        m.put("password", new String(up.getPassword()));
        return m;
    }

    @Override
    public String exportAsFile(@InjectVariable("txId") String instanceId,
                               @InjectVariable("workDir") String workDir,
                               String name,
                               String password) throws Exception {

        return exportAsFile(instanceId, workDir, null, name, password);
    }

    @Override
    public String exportAsFile(@InjectVariable("txId") String instanceId,
                               @InjectVariable("workDir") String workDir,
                               String orgName,
                               String name,
                               String password) throws Exception {

        Secret s = get(instanceId, orgName, name, password);
        if (!(s instanceof BinaryDataSecret)) {
            throw new IllegalArgumentException("Expected a single value secret");
        }

        BinaryDataSecret bds = (BinaryDataSecret) s;

        Path baseDir = Paths.get(workDir);
        Path tmpDir = assertTempDir(baseDir);

        Path p = Files.createTempFile(tmpDir, "file", ".bin");
        Files.write(p, bds.getData());

        return baseDir.relativize(p).toString();
    }

    @Override
    public String decryptString(@InjectVariable("txId") String instanceId, String s) throws Exception {
        return secretReaderService.decryptString(instanceId, s);
    }

    private Secret get(String instanceId, String orgName, String name, String password) throws Exception {
        Secret s = secretReaderService.fetch(instanceId, orgName, name, password);
        if (s == null) {
            throw new IllegalArgumentException("Secret not found: " + name);
        }
        return s;
    }

    private static Path assertTempDir(Path baseDir) throws IOException {
        Path p = baseDir.resolve(".tmp");
        if (!Files.exists(p)) {
            Files.createDirectories(p);
        }
        return p;
    }
}
