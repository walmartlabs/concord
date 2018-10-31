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

import com.walmartlabs.concord.server.org.policy.PolicyEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.ProcessKey;
import com.walmartlabs.concord.server.process.logs.LogManager;
import com.walmartlabs.concord.server.process.pipelines.processors.policy.PolicyApplier;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;
import java.util.Set;

/**
 * Applies policies.
 */
@Named
public class PolicyProcessor implements PayloadProcessor {

    private final LogManager logManager;
    private final Set<PolicyApplier> appliers;

    @Inject
    public PolicyProcessor(LogManager logManager, Set<PolicyApplier> appliers) {
        this.logManager = logManager;
        this.appliers = appliers;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();

        PolicyEntry policy = payload.getHeader(Payload.POLICY);
        if (policy == null || policy.isEmpty()) {
            return chain.process(payload);
        }

        logManager.info(processKey, "Applying policies...");
        Map<String, Object> rules = policy.getRules();

        try {
            // TODO merge check results
            for (PolicyApplier a : appliers) {
                a.apply(payload, rules);
            }
        } catch (ProcessException e) {
            throw e;
        } catch (Exception e) {
            logManager.error(processKey, "Error while applying policy '{}': {}", policy.getName(), e);
            throw new ProcessException(processKey, "Policy '" + policy.getName() + "' error", e);
        }

        return chain.process(payload);
    }
}
