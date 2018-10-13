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
import com.walmartlabs.concord.policyengine.FileRule;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.server.metrics.InjectCounter;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.LogManager;

import javax.inject.Inject;
import javax.inject.Named;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Named
public class FilePolicyApplier implements PolicyApplier {

    private final LogManager logManager;

    @InjectCounter
    private final Counter policyWarn;

    @InjectCounter
    private final Counter policyDeny;

    @Inject
    public FilePolicyApplier(LogManager logManager, Counter policyWarn, Counter policyDeny) {
        this.logManager = logManager;
        this.policyWarn = policyWarn;
        this.policyDeny = policyDeny;
    }

    @Override
    public void apply(Payload payload, Map<String, Object> policy) throws Exception {
        UUID instanceId = payload.getInstanceId();
        Path workDir = payload.getHeader(Payload.WORKSPACE_DIR);

        CheckResult<FileRule, Path> result = new PolicyEngine(policy).getFilePolicy().check(workDir);

        result.getWarn().forEach(i -> {
            policyWarn.inc();
            logManager.warn(instanceId, "Potentially restricted file '{}' (file policy: {})",
                    workDir.relativize(i.getEntity()), i.getRule());
        });

        result.getDeny().forEach(i -> {
            policyDeny.inc();
            logManager.error(instanceId, "File '{}' is forbidden by the file policy {}",
                    workDir.relativize(i.getEntity()), i.getRule());
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(instanceId, "Found forbidden files");
        }
    }
}
