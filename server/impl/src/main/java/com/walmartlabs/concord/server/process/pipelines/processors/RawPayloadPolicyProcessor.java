package com.walmartlabs.concord.server.process.pipelines.processors;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.RawPayloadRule;
import com.walmartlabs.concord.server.policy.PolicyManager;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.sdk.ProcessKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

public class RawPayloadPolicyProcessor implements PayloadProcessor{

    private static final String DEFAULT_POLICY_MESSAGE = "Raw payload size too big: current {0} byte(s), limit {1} byte(s)";
    
    private static final Logger log = LoggerFactory.getLogger(RawPayloadPolicyProcessor.class);

    private final PolicyManager policyManager;
    
    @Inject
    public RawPayloadPolicyProcessor(PolicyManager policyManager) {
        this.policyManager = policyManager;
    }

    @Override
    public Payload process(Chain chain, Payload payload) {
        ProcessKey processKey = payload.getProcessKey();
        
        PolicyEngine policy = policyManager.getPolicyEngine(payload);
        if (policy == null) {
            return chain.process(payload);
        }

        if (payload.getAttachment(Payload.WORKSPACE_ARCHIVE) == null) {
            return chain.process(payload);
        }
        
        CheckResult<RawPayloadRule, Long> result;
        try {
            result = policy.getRawPayloadPolicy()
                    .check(payload.getAttachment(Payload.WORKSPACE_ARCHIVE));
        } catch (Exception e) {
            log.error("process -> error", e);
            throw new ProcessException(processKey, "Found raw payload policy check error", e);
        }

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(processKey, String.format("Found raw payload policy violations: %s", buildErrorMessage(result.getDeny())), Response.Status.BAD_REQUEST);
        }

        return chain.process(payload);
    }

    private String buildErrorMessage(List<CheckResult.Item<RawPayloadRule, Long>> errors) {
        StringBuilder sb = new StringBuilder();
        for (CheckResult.Item<RawPayloadRule, Long> e : errors) {
            RawPayloadRule r = e.getRule();

            String msg = r.msg() != null ? r.msg() : DEFAULT_POLICY_MESSAGE;
            long actualCount = e.getEntity();
            long limit = r.maxSizeInBytes();

            sb.append(MessageFormat.format(Objects.requireNonNull(msg), actualCount, limit)).append(';');
        }
        return sb.toString();
    }
    
}
