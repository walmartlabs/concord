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
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Adds policy to a payload.
 */
@Named
public class PolicyExportProcessor implements PayloadProcessor {

    private final PolicyDao policyDao;
    private final LogManager logManager;
    private final ObjectMapper objectMapper;

    @Inject
    public PolicyExportProcessor(PolicyDao policyDao, LogManager logManager) {
        this.policyDao = policyDao;
        this.logManager = logManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        UUID orgId = payload.getHeader(Payload.ORGANIZATION_ID);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);
        UUID userId = payload.getHeader(Payload.INITIATOR_ID);

        PolicyEntry policy = policyDao.getLinked(orgId, projectId, userId);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Storing policy '{}' data", policy.getName());

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path dst = Files.createDirectories(ws.resolve(InternalConstants.Files.CONCORD_SYSTEM_DIR_NAME));
            objectMapper.writeValue(dst.resolve(InternalConstants.Files.POLICY_FILE_NAME).toFile(), policy.getRules());

        } catch (IOException e) {
            logManager.error(processKey, "Error while storing process policy: {}", e);
            throw new ProcessException(processKey, "Storing process policy error", e);
        }

        payload = payload.putHeader(Payload.POLICY, policy);

        return chain.process(payload);
    }
}
