package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.walmartlabs.concord.project.InternalConstants;
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
import java.util.*;

@Named
public class ProcessInfoProcessor implements PayloadProcessor {

    private final SecretStoreConfiguration secretCfg;
    private final ProcessLogManager logManager;

    @Inject
    public ProcessInfoProcessor(SecretStoreConfiguration secretCfg, ProcessLogManager logManager) {
        this.secretCfg = secretCfg;
        this.logManager = logManager;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            cfg = new HashMap<>();
        }

        Map<String, Object> args = (Map<String, Object>) cfg.get(Constants.Request.ARGUMENTS_KEY);
        if (args == null) {
            args = new HashMap<>();
            cfg.put(Constants.Request.ARGUMENTS_KEY, args);
        }

        String token = createSessionKey(payload.getProcessKey());
        Map<String, Object> m = new HashMap<>();
        m.put("sessionKey", token);

        Collection<String> activeProfiles = payload.getHeader(Payload.ACTIVE_PROFILES);
        if (activeProfiles == null) {
            activeProfiles = Collections.emptyList();
        }
        m.put("activeProfiles", activeProfiles);

        args.put(Constants.Request.PROCESS_INFO_KEY, m);

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);
        exportSessionToken(processKey, ws, token);

        return chain.process(payload.putHeader(Payload.CONFIGURATION, cfg));
    }

    private void exportSessionToken(ProcessKey processKey, Path ws, String token) {
        try {
            Path dst = Files.createDirectories(ws.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME));
            Files.write(dst.resolve(InternalConstants.Files.SESSION_TOKEN_FILE_NAME), token.getBytes());
        } catch (IOException e) {
            logManager.error(processKey, "Error while storing the session token: {}", e);
            throw new ProcessException(processKey, "Error while string the session token", e);
        }
    }

    private String createSessionKey(PartialProcessKey processKey) {
        byte[] salt = secretCfg.getSecretStoreSalt();
        byte[] pwd = secretCfg.getServerPwd();

        UUID instanceId = processKey.getInstanceId();

        byte[] ab = SecretUtils.encrypt(instanceId.toString().getBytes(), pwd, salt);
        return Base64.getEncoder().encodeToString(ab);
    }
}
