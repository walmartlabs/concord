package com.walmartlabs.concord.server.process.pipelines.processors.policy;

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

import com.codahale.metrics.Counter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.ContainerRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static com.walmartlabs.concord.server.process.pipelines.processors.policy.PolicyApplier.appendMsg;

public class ContainerPolicyApplier implements PolicyApplier {

    private final ProcessLogManager logManager;

    @InjectCounter
    private final Counter policyWarn;

    @InjectCounter
    private final Counter policyDeny;

    private final ObjectMapper objectMapper;

    @Inject
    public ContainerPolicyApplier(ProcessLogManager logManager, Counter policyWarn, Counter policyDeny) {
        this.logManager = logManager;
        this.policyWarn = policyWarn;
        this.policyDeny = policyDeny;

        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void apply(Payload payload, PolicyEngine policy) {
        ProcessKey processKey = payload.getProcessKey();
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);

        Path p = workDir.resolve(Constants.Files.CONFIGURATION_FILE_NAME);
        if (!Files.exists(p)) {
            return;
        }

        Map<String, Object> containerOptions;
        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            containerOptions = (Map<String, Object>) m.get(Constants.Request.CONTAINER);
        } catch (IOException e) {
            logManager.error(processKey, "Error while reading container configuration: {}", e);
            throw new ProcessException(processKey, "Error while reading container configuration", e);
        }

        CheckResult<ContainerRule, Object> result = policy.getContainerPolicy().check(containerOptions);

        result.getWarn().forEach(i -> {
            policyWarn.inc();
            logManager.warn(processKey, appendMsg("Potential container policy violation (policy: {})", i.getMsg()), i.getRule());
        });

        result.getDeny().forEach(i -> {
            policyDeny.inc();
            logManager.error(processKey, appendMsg("Container policy violation", i.getMsg()), i.getRule());
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(processKey, "Found container policy violations");
        }
    }
}
