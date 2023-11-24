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
import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.ProcessTimeoutRule;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.util.Map;

public class ProcessTimeoutPolicyApplier implements PolicyApplier {

    private static final String DEFAULT_PROCESS_TIMEOUT_MSG = "Maximum 'processTimeout' value exceeded: current {0}, limit {1}";

    private final ProcessLogManager logManager;

    @InjectCounter
    private final Counter policyDeny;

    @Inject
    public ProcessTimeoutPolicyApplier(ProcessLogManager logManager, Counter policyDeny) {
        this.logManager = logManager;
        this.policyDeny = policyDeny;
    }

    @Override
    public void apply(Payload payload, PolicyEngine policy) {
        ProcessKey processKey = payload.getProcessKey();

        Map<String, Object> cfg = payload.getHeader(Payload.CONFIGURATION);
        if (cfg == null) {
            return;
        }

        Object processTimeout = cfg.get(Constants.Request.PROCESS_TIMEOUT);
        if (processTimeout == null) {
            return;
        }

        CheckResult<ProcessTimeoutRule, Object> result = policy.getProcessTimeoutPolicy().check(processTimeout);

        result.getDeny().forEach(i -> {
            policyDeny.inc();

            String msg = i.getRule().msg() != null ? i.getRule().msg() : DEFAULT_PROCESS_TIMEOUT_MSG;
            Object actualTimeout = i.getEntity();
            String limit = i.getRule().max();
            logManager.error(processKey, MessageFormat.format(msg, actualTimeout, limit));
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(processKey, "'processTimeout' value policy violation");
        }
    }
}
