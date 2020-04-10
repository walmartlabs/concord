package com.walmartlabs.concord.server.process.pipelines.processors;

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
import com.walmartlabs.concord.server.cfg.SecretStoreConfiguration;
import com.walmartlabs.concord.server.org.secret.SecretUtils;
import com.walmartlabs.concord.server.process.PartialProcessKey;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

/**
 * Generates and saves the process' session token (key).
 */
@Named
public class SessionTokenProcessor implements PayloadProcessor {

    private final SecretStoreConfiguration secretCfg;
    private final ProcessLogManager logManager;

    @Inject
    public SessionTokenProcessor(SecretStoreConfiguration secretCfg, ProcessLogManager logManager) {
        this.secretCfg = secretCfg;
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);

        String token = createSessionToken(payload.getProcessKey());
        payload = payload.putHeader(Payload.SESSION_TOKEN, token);

        try {
            Path dst = Files.createDirectories(workDir.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME));
            Files.write(dst.resolve(Constants.Files.SESSION_TOKEN_FILE_NAME), token.getBytes());
        } catch (IOException e) {
            logManager.error(processKey, "Error while storing the session token: {}", e);
            throw new ProcessException(processKey, "Error while string the session token", e);
        }

        return chain.process(payload);
    }

    private String createSessionToken(PartialProcessKey processKey) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        UUID instanceId = processKey.getInstanceId();

        byte[] ab = SecretUtils.encrypt(instanceId.toString().getBytes(), pwd, salt);
        return Base64.getEncoder().encodeToString(ab);
    }
}
