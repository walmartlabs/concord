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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.ApiResponse;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.SecretOperationResponse;
import com.walmartlabs.concord.runtime.v2.model.ProcessConfiguration;
import com.walmartlabs.concord.runtime.v2.model.ProjectInfo;
import com.walmartlabs.concord.runtime.v2.sdk.*;
import com.walmartlabs.concord.runtime.v2.sdk.SecretService.KeyPair;
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
    private final ApiClient apiClient;
    private final String processOrg;

    @Inject
    public CryptoTaskV2(ApiClient apiClient, Context context) {
        this.secretService = context.secretService();
        this.workDir = context.workingDirectory();
        this.processCfg = context.processConfiguration();
        this.apiClient = apiClient;
        this.processOrg = context.projectInfo() != null ? context.projectInfo().orgName() : null;
    }

    public String exportAsString(String orgName, String name, String password) throws Exception {
        return secretService.exportAsString(orgName, name, password);
    }

    public Map<String, String> exportKeyAsFile(String orgName, String name, String password) throws Exception {
        KeyPair keyPair = secretService.exportKeyAsFile(orgName, name, password);

        Path baseDir = workDir;

        Map<String, String> m = new HashMap<>();
        m.put("private", baseDir.relativize(keyPair.privateKey()).toString());
        m.put("public", baseDir.relativize(keyPair.publicKey()).toString());
        return m;
    }

    public Map<String, String> exportCredentials(String orgName, String name, String password) throws Exception {
        UsernamePassword credentials = secretService.exportCredentials(orgName, name, password);

        Map<String, String> m = new HashMap<>();
        m.put("username", credentials.username());
        m.put("password", credentials.password());
        return m;
    }

    public String exportAsFile(String orgName, String name, String password) throws Exception {
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
                SecretOperationResponse result = createSecret(params);
                log.info("The secret '{}/{}' was created", params.org(processOrg), params.name());
                return new TaskResult(result.isOk(), null)
                        .value("password", result.getPassword());
            }
            default:
                throw new IllegalArgumentException("Unsupported action type: " + action);
        }
    }

    private SecretOperationResponse createSecret(TaskParams in) throws IOException {
        String path = "/api/v1/org/" + in.org(processOrg) + "/secret";

        Map<String, Object> params = new HashMap<>();
        params.put("name", in.name());
        params.put("generatePassword", in.generatePassword());
        if (in.storePassword() != null) {
            params.put("storePassword", in.storePassword());
        }
        if (in.visibility() != null) {
            params.put("visibility", in.visibility());
        }
        if (in.project() != null) {
            params.put("project", in.project());
        }

        if (in.data() != null) {
            params.put("type", "DATA");
            params.put("data", readFile(in.data()));
        } else if (in.keyPair() != null) {
            params.put("type", "KEY_PAIR");
            TaskParams.KeyPair kp = in.keyPair();
            params.put("public", readFile(kp.publicKey()));
            params.put("private", readFile(kp.privateKey()));
        } else if (in.usernamePassword() != null){
            params.put("type", "USERNAME_PASSWORD");
            TaskParams.UsernamePassword up = in.usernamePassword();
            params.put("username", up.username());
            params.put("password", up.password());
        } else {
            throw new IllegalArgumentException("No secret data defined");
        }

        try {
            ApiResponse<SecretOperationResponse> response = ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL,
                    () -> ClientUtils.postData(apiClient, path, params, SecretOperationResponse.class));
            return response.getData();
        } catch (ApiException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private byte[] readFile(String fileName) throws IOException {
        Path p = workDir.resolve(fileName);
        if (Files.notExists(p)) {
            throw new RuntimeException("File '" + fileName + "' not found");
        }
        return Files.readAllBytes(p);
    }
}
