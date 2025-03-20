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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Adds policy to a payload.
 */
public class PolicyExportProcessor implements PayloadProcessor {

    private final ObjectMapper objectMapper;
    private final PolicyManager policyManager;
    private final ProcessLogManager logManager;

    @Inject
    public PolicyExportProcessor(ObjectMapper objectMapper, PolicyManager policyManager, ProcessLogManager logManager) {
        this.objectMapper = objectMapper;
        this.policyManager = policyManager;
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        UUID orgId = payload.getHeader(Payload.ORGANIZATION_ID);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID userId = payload.getHeader(Payload.INITIATOR_ID);

        PolicyEngine policy = policyManager.get(orgId, projectId, userId);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Storing policy '{}' data", policy.policyNames());

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path dst = Files.createDirectories(ws.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME));
            objectMapper.writeValue(dst.resolve(Constants.Files.POLICY_FILE_NAME).toFile(), policy.getRules());
        } catch (IOException e) {
            logManager.error(processKey, "Error while storing process policy: {}", e);
            throw new ProcessException(processKey, "Storing process policy error", e);
        }

        payload = payload.putHeader(Payload.POLICY, policy);

        return chain.process(payload);
    }
}
