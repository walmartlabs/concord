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
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.api.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.metrics.WithTimer;
import com.walmartlabs.concord.server.org.policy.PolicyDao;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
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
public class PolicyProcessor implements PayloadProcessor {

    private final PolicyDao policyDao;
    private final LogManager logManager;
    private final ObjectMapper objectMapper;

    @Inject
    public PolicyProcessor(PolicyDao policyDao, LogManager logManager) {
        this.policyDao = policyDao;
        this.logManager = logManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @WithTimer
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();

        UUID orgId = payload.getHeader(Payload.ORGANIZATION_ID);
        UUID projectId = payload.getHeader(Payload.PROJECT_ID);

        PolicyEntry policy = policyDao.getLinked(orgId, projectId);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(instanceId, "Storing policy '{}' data", policy.getName());

        Path ws = payload.getHeader(Payload.WORKSPACE_DIR);

        try {
            Path dst = Files.createDirectories(ws.resolve(InternalConstants.Files.CONCORD));

            objectMapper.writeValue(dst.resolve(InternalConstants.Files.POLICY).toFile(), policy.getRules());

            return chain.process(payload);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while storing process policy: {}", e);
            throw new ProcessException(instanceId, "Storing process policy error", e);
        }
    }
}
