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
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.plugins.crypto.TaskParams.*;
import static java.nio.charset.StandardCharsets.UTF_8;

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
    private final boolean dryRunMode;

    @Inject
    public CryptoTaskV2(Context context) {
        this.secretService = context.secretService();
        this.workDir = context.workingDirectory();
        this.processCfg = context.processConfiguration();

        ProjectInfo projectInfo = processCfg.projectInfo();
        this.processOrg = projectInfo != null ? projectInfo.orgName() : null;
        this.dryRunMode = processCfg.dryRun();
    }

    @SensitiveData
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

    @SensitiveData(keys = {"password"})
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

    public String exportAsFile(String exportDir, String orgName, String name, @SensitiveData String password) throws Exception {
        Path path = secretService.exportAsFile(orgName, name, password);
        Path dest = workDir.resolve(exportDir);

        Files.createDirectories(dest);

        Path destFileName = dest.resolve(path.getFileName());
        Files.move(path, destFileName, StandardCopyOption.REPLACE_EXISTING);

        return workDir.relativize(destFileName).toString();
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

    @SensitiveData
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
                if (result == null) {
                    return TaskResult.success();
                }

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
        String stringValue = in.stringValue();
        TaskParams.KeyPair kp = in.keyPair();
        TaskParams.UsernamePassword up = in.usernamePassword();

        if (data != null && stringValue != null) {
            throw new IllegalArgumentException("The 'data' parameter and 'stringValue' are mutually exclusive.");
        }


        if (data != null) {
            if (dryRunMode) {
                log.info("Dry-run mode enabled: Skipping creation of a data secret '{}'", in.secretName());
                return null;
            }

            Path file = toPath(data);
            if (Files.notExists(file)) {
                throw new RuntimeException("Can't create a data secret. File '%s' not found".formatted(file));
            }

            byte[] ab = Files.readAllBytes(file);
            return secretService.createData(secret, ab);
        } else if (stringValue != null) {
            if (dryRunMode) {
                log.info("Dry-run mode enabled: Skipping creation of a string secret '{}'", in.secretName());
                return null;
            }

            byte[] ab = stringValue.getBytes(UTF_8);
            return secretService.createData(secret, ab);
        } else if (kp != null) {
            Path publicKey = toPath(kp.publicKey());
            Path privateKey = toPath(kp.privateKey());

            if (dryRunMode) {
                log.info("Dry-run mode enabled: Skipping creation of a key-pair secret '{}'", in.secretName());
                return null;
            }

            return secretService.createKeyPair(secret, KeyPair.builder()
                    .publicKey(publicKey)
                    .privateKey(privateKey)
                    .build());
        } else if (up != null) {
            if (dryRunMode) {
                log.info("Dry-run mode enabled: Skipping creation of a username/password secret '{}'", in.secretName());
                return null;
            }

            return secretService.createUsernamePassword(secret, UsernamePassword.of(up.username(), up.password()));
        } else {
            throw new IllegalArgumentException("One of '%s', '%s', '%s' or '%s' is required"
                    .formatted(DATA_KEY, STRING_VALUE_KEY, KEY_PAIR_KEY, USERNAME_PASSWORD_KEY));
        }
    }

    private Path toPath(String value) {
        Path p = workDir.resolve(value).normalize();

        if (!p.startsWith(workDir)) {
            throw new IllegalArgumentException("Can't use paths outside of the working directory: %s".formatted(p.toAbsolutePath()));
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
