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
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.policy.PolicyApplier;
import com.walmartlabs.concord.server.sdk.ProcessKey;

import javax.inject.Inject;
import java.util.Set;

/**
 * Applies policies.
 */
public class PolicyProcessor implements PayloadProcessor {

    private final ProcessLogManager logManager;
    private final Set<PolicyApplier> appliers;

    @Inject
    public PolicyProcessor(ProcessLogManager logManager, Set<PolicyApplier> appliers) {
        this.logManager = logManager;
        this.appliers = appliers;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        PolicyEngine policy = payload.getHeader(Payload.POLICY);
        if (policy == null) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Applying policies...");

        try {
            // TODO merge check results
            for (PolicyApplier a : appliers) {
                a.apply(payload, policy);
            }
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            logManager.error(processKey, "Error while applying policy '{}': {}", policy.policyNames(), e);
            throw new ProcessException(processKey, "Policy '" + policy.policyNames() + "' error", e);
        }

        return chain.process(payload);
    }
}
