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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;

/**
 * Loads the policy for server-side payload processing without persisting it in the workspace.
 */
public class PolicyExportProcessor implements PayloadProcessor {

    private final PolicyManager policyManager;
    private final ProcessLogManager logManager;

    @Inject
    public PolicyExportProcessor(PolicyManager policyManager, ProcessLogManager logManager) {
        this.policyManager = policyManager;
        this.logManager = logManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        PolicyEngine policy = policyManager.getPolicyEngine(payload);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Applying policy '{}' data", policy.policyNames());

        payload = payload.putHeader(Payload.POLICY, policy);

        return chain.process(payload);
    }
}
