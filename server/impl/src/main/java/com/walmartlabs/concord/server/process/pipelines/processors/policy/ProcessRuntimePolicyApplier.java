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
import com.walmartlabs.concord.policyengine.RuntimeRule;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;

import javax.inject.Inject;
import javax.inject.Named;
import java.text.MessageFormat;
import java.util.Set;

@Named
public class ProcessRuntimePolicyApplier implements PolicyApplier {

    private static final String DEFAULT_PROCESS_TIMEOUT_MSG = "{0} runtime version is not allowed";

    private final ProcessLogManager logManager;

    @InjectCounter
    private final Counter policyDeny;

    @Inject
    public ProcessRuntimePolicyApplier(ProcessLogManager logManager, Counter policyDeny) {
        this.logManager = logManager;
        this.policyDeny = policyDeny;
    }

    @Override
    public void apply(Payload payload, PolicyEngine policy) {
        String runtime = payload.getHeader(Payload.RUNTIME);
        CheckResult<RuntimeRule, String> result = policy.getRuntimePolicy().check(runtime);

        result.getDeny().forEach(i -> {
            policyDeny.inc();

            String msg = i.getRule().getMsg() != null ? i.getRule().getMsg() : DEFAULT_PROCESS_TIMEOUT_MSG;
            Object actualRuntime = i.getEntity();
            Set<String> allowed = i.getRule().getAllowedRuntimes();
            logManager.error(payload.getProcessKey(), MessageFormat.format(msg, actualRuntime, allowed));
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(payload.getProcessKey(), "'configuration.runtime' value policy violation");
        }
    }
}
