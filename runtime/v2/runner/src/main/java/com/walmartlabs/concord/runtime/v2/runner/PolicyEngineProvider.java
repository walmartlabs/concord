package com.walmartlabs.concord.runtime.v2.runner;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.PolicyEngineRules;
import com.walmartlabs.concord.runtime.v2.sdk.WorkingDirectory;
import com.walmartlabs.concord.sdk.Constants;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class PolicyEngineProvider implements Provider<PolicyEngine> {

    private final ObjectMapper objectMapper;
    private final WorkingDirectory workDir;

    @Inject
    public PolicyEngineProvider(ObjectMapper objectMapper, WorkingDirectory workDir) {
        this.objectMapper = objectMapper;
        this.workDir = workDir;
    }

    @Override
    public PolicyEngine get() {
        try {
            PolicyEngineRules rules = readPolicyRules(workDir.getValue());
            return new PolicyEngine(rules);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PolicyEngineRules readPolicyRules(Path ws) {
        Path policyFile = ws.resolve(Constants.Files.CONCORD_SYSTEM_DIR_NAME).resolve(Constants.Files.POLICY_FILE_NAME);
        if (!Files.exists(policyFile)) {
            return PolicyEngineRules.builder().build();
        }

        try {
            return objectMapper.readValue(policyFile.toFile(), PolicyEngineRules.class);
        } catch (IOException e) {
            throw new RuntimeException("Error while reading policy rules: " + e.getMessage());
        }
    }
}
