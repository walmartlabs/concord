package com.walmartlabs.concord.plugins.crypto;

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

import com.walmartlabs.concord.runtime.v2.sdk.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.sdk.ProjectInfo;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.KeyPair;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.SecretCreationResult;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.UsernamePassword;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Named("crypto")
@SuppressWarnings("unused")
public class CryptoTaskV2 implements Task {

    private static final Logger log = LoggerFactory.getLogger(CryptoTaskV2.class);

    private static final int RETRY_COUNT = 3;
    private static final int RETRY_INTERVAL = 5000;

    private final SecretService secretService;
    private final Path workDir;
    private final ProcessConfiguration processCfg;
    private final String processOrg;

    @Inject
    public CryptoTaskV2(Context context) {
        this.secretService = context.secretService();
        this.workDir = context.workingDirectory();
        this.processCfg = context.processConfiguration();

        ProjectInfo projectInfo = processCfg.projectInfo();
        this.processOrg = projectInfo != null ? projectInfo.orgName() : null;
    }

    public String exportAsString(String orgName, String name, @SensitiveData String password) throws Exception {
        return secretService.exportAsString(orgName, name, password);
    }

    public Map<String, String> exportKeyAsFile(String orgName, String name, @SensitiveData String password) throws Exception {
        KeyPair keyPair = secretService.exportKeyAsFile(orgName, name, password);

        Path baseDir = workDir;

        Map<String, String> m = new HashMap<>();
        m.put("private", baseDir.relativize(keyPair.privateKey()).toString());
        m.put("public", baseDir.relativize(keyPair.publicKey()).toString());
        return m;
    }

    public Map<String, String> exportCredentials(String orgName, String name, @SensitiveData String password) throws Exception {
        UsernamePassword credentials = secretService.exportCredentials(orgName, name, password);

        Map<String, String> m = new HashMap<>();
        m.put("username", credentials.username());
        m.put("password", credentials.password());
        return m;
    }

    public String exportAsFile(String orgName, String name, @SensitiveData String password) throws Exception {
        Path path = secretService.exportAsFile(orgName, name, password);
        return workDir.relativize(path).toString();
    }

    public String encryptString(String value) throws Exception {
        ProjectInfo projectInfo = processCfg.projectInfo();

        String orgName = projectInfo.orgName();
        String projectName = projectInfo.projectName();
        if (orgName == null || projectName == null) {
            throw new IllegalStateException("The process must run in a project in order to be able to encrypt strings.");
        }

        return secretService.encryptString(orgName, projectName, value);
    }

    public String decryptString(String s) throws Exception {
        return secretService.decryptString(s);
    }

    @Override
    public TaskResult execute(Variables input) throws Exception {
        TaskParams params = new TaskParams(input);
        TaskParams.Action action = params.action();
        switch (action) {
            case CREATE: {
                SecretCreationResult result = createSecret(params);
                log.info("The secret '{}/{}' was created successfully", params.orgOrDefault(processOrg), params.secretName());
                return TaskResult.success()
                        .value("password", result.password());
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private SecretCreationResult createSecret(TaskParams in) throws Exception {
        SecretService.SecretParams secret = SecretService.SecretParams.builder()
                .orgName(in.orgOrDefault(processOrg))
                .secretName(in.secretName())
                .generatePassword(in.generatePassword())
                .storePassword(in.storePassword())
                .visibility(in.visibility() != null ? SecretService.SecretParams.Visibility.valueOf(in.visibility()) : null)
                .project(in.project())
                .build();

        String data = in.data();
        TaskParams.KeyPair kp = in.keyPair();
        TaskParams.UsernamePassword up = in.usernamePassword();

        if (data != null) {
            return secretService.createData(secret, readFile(toPath(data)));
        } else if (kp != null) {
            return secretService.createKeyPair(secret, KeyPair.builder()
                    .publicKey(toPath(kp.publicKey()))
                    .privateKey(toPath(kp.privateKey()))
                    .build());
        } else if (up != null) {
            return secretService.createUsernamePassword(secret, UsernamePassword.of(up.username(), up.password()));
        } else {
            throw new IllegalArgumentException("A path to the secret's data, a key pair or a username/password pair must be specified.");
        }

    }

    private Path toPath(String value) {
        Path p = workDir.resolve(value).normalize();

        if (!p.startsWith(workDir)) {
            throw new IllegalArgumentException("Can't use paths outside of the working directory: " + p.toAbsolutePath());
        }

        return p;
    }

    private static byte[] readFile(Path file) throws IOException {
        if (Files.notExists(file)) {
            throw new RuntimeException("File '" + file + "' not found");
        }

        return Files.readAllBytes(file);
    }
}
