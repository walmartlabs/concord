package com.walmartlabs.concord.server.process.pipelines.processors;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.FileRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Applies file policies.
 */
@Named
public class FilePolicyProcessor implements PayloadProcessor {

    private final LogManager logManager;
    private final ObjectMapper objectMapper;

    @Inject
    public FilePolicyProcessor(LogManager logManager) {
        this.logManager = logManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);
        Path policyFile = ws.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(InternalConstants.Files.POLICY_FILE_NAME);
        if (!Files.exists(policyFile)) {
            return chain.process(payload);
        }

        logManager.info(instanceId, "Checking file policy...");

        Map<String, Object> policy = readPolicy(instanceId, policyFile);

        try {
            CheckResult<FileRule, Path> result = new PolicyEngine(policy).getFilePolicy().check(ws);

            result.getWarn().forEach(i -> {
                logManager.warn(instanceId, "Potentially restricted file '{}' (file policy: {})",
                        ws.relativize(i.getEntity()), i.getRule());
            });

            result.getDeny().forEach(i -> {
                logManager.error(instanceId, "File '{}' is forbidden by the file policy {}",
                        ws.relativize(i.getEntity()), i.getRule());
            });

            if (!result.getDeny().isEmpty()) {
                throw new ProcessException(instanceId, "Found forbidden files");
            }

            return chain.process(payload);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while checking files policy: {}", e);
            throw new ProcessException(instanceId, "Checking files policy error", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPolicy(UUID instanceId, Path policyFile) {
        try {
            return objectMapper.readValue(policyFile.toFile(), Map.class);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while reading policy: {}", e);
            throw new ProcessException(instanceId, "Reading process policy error", e);
        }
    }
}
