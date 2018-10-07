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
import com.walmartlabs.concord.policyengine.*;
import com.walmartlabs.concord.project.InternalConstants;
import com.walmartlabs.concord.server.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Applies policies.
 */
@Named
public class PolicyProcessor implements PayloadProcessor {

    private final LogManager logManager;
    private final ObjectMapper objectMapper;

    @Inject
    public PolicyProcessor(LogManager logManager) {
        this.logManager = logManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        UUID instanceId = payload.getInstanceId();
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);

        PolicyEntry policy = payload.getHeader(Payload.POLICY);
        if (policy == null || policy.isEmpty()) {
            return chain.process(payload);
        }

        logManager.info(instanceId, "Applying policies...");

        Map<String, Object> rules = policy.getRules();

        try {
            // TODO merge check results
            applyWorkspacePolicy(instanceId, rules, workDir);
            applyFilePolicy(instanceId, rules, workDir);
            applyContainerPolicy(instanceId, rules, workDir);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while applying policy '{}': {}", policy.getName(), e);
            throw new ProcessException(instanceId, "Policy '" + policy.getName() + "' error", e);
        }

        return chain.process(payload);
    }

    @SuppressWarnings("unchecked")
    private void applyContainerPolicy(UUID instanceId, Map<String, Object> policy, Path workDir) {
        Path p = workDir.resolve(InternalConstants.Files.REQUEST_DATA_FILE_NAME);
        if (!Files.exists(p)) {
            return;
        }

        Map<String, Object> containerOptions;
        try (InputStream in = Files.newInputStream(p)) {
            Map<String, Object> m = objectMapper.readValue(in, Map.class);
            containerOptions = (Map<String, Object>) m.get(InternalConstants.Request.CONTAINER);
        } catch (IOException e) {
            logManager.error(instanceId, "Error while reading container configuration: {}", e);
            throw new ProcessException(instanceId, "Error while reading container configuration", e);
        }

        CheckResult<ContainerRule, Object> result = new PolicyEngine(policy).getContainerPolicy().check(containerOptions);

        result.getWarn().forEach(i -> {
            logManager.warn(instanceId, appendMsg("Potential container policy violation (policy: {})", i.getMsg()), i.getRule());
        });

        result.getDeny().forEach(i -> {
            logManager.error(instanceId, appendMsg("Container policy violation", i.getMsg()), i.getRule());
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(instanceId, "Found container policy violations");
        }
    }

    private void applyWorkspacePolicy(UUID instanceId, Map<String, Object> policy, Path workDir) throws IOException {
        CheckResult<WorkspaceRule, Path> result = new PolicyEngine(policy).getWorkspacePolicy().check(workDir);

        result.getWarn().forEach(i -> {
            logManager.warn(instanceId, appendMsg("Potential workspace policy violation (policy: {})", i.getMsg()), i.getRule());
        });

        result.getDeny().forEach(i -> {
            logManager.error(instanceId, appendMsg("Workspace policy violation", i.getMsg()), i.getRule());
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(instanceId, "Found workspace policy violations");
        }
    }

    private void applyFilePolicy(UUID instanceId, Map<String, Object> policy, Path workDir) throws IOException {
        CheckResult<FileRule, Path> result = new PolicyEngine(policy).getFilePolicy().check(workDir);

        result.getWarn().forEach(i -> {
            logManager.warn(instanceId, "Potentially restricted file '{}' (file policy: {})",
                    workDir.relativize(i.getEntity()), i.getRule());
        });

        result.getDeny().forEach(i -> {
            logManager.error(instanceId, "File '{}' is forbidden by the file policy {}",
                    workDir.relativize(i.getEntity()), i.getRule());
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(instanceId, "Found forbidden files");
        }
    }

    private static String appendMsg(String msg, String s) {
        if (s == null) {
            return msg;
        }
        return msg + ": " + s;
    }
}
