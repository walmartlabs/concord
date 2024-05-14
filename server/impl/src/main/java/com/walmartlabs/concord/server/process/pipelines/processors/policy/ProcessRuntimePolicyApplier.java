package com.walmartlabs.concord.server.process.pipelines.processors.policy;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.org.project.ProjectDao;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.process.Payload;
import com.walmartlabs.concord.server.process.ProcessException;
import com.walmartlabs.concord.server.process.logs.ProcessLogManager;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;

import javax.inject.Inject;
import java.text.MessageFormat;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class ProcessRuntimePolicyApplier implements PolicyApplier {

    private static final String DEFAULT_PROCESS_TIMEOUT_MSG = "{0} runtime version is not allowed";

    private final ProcessLogManager logManager;

    @InjectCounter
    private final Counter policyDeny;
    private final ProjectDao projectDao;

    @Inject
    public ProcessRuntimePolicyApplier(ProcessLogManager logManager, Counter policyDeny, ProjectDao projectDao) {
        this.logManager = logManager;
        this.policyDeny = policyDeny;
        this.projectDao = projectDao;
    }

    @Override
    public void apply(Payload payload, PolicyEngine policy) {
        String runtime = payload.getHeader(Payload.RUNTIME);

        Supplier<OffsetDateTime> projectCreatedAt = () -> {
            UUID projectId = payload.getHeader(Payload.PROJECT_ID);
            if (projectId == null) {
                return null;
            }
            ProjectEntry projectEntry = projectDao.get(projectId);
            if (projectEntry != null) {
                return projectEntry.getCreatedAt();
            }

            return null;
        };

        CheckResult<RuntimeRule, String> result = policy.getRuntimePolicy().check(runtime, projectCreatedAt);

        result.getDeny().forEach(i -> {
            policyDeny.inc();

            String msg = i.getRule().msg() != null ? i.getRule().msg() : DEFAULT_PROCESS_TIMEOUT_MSG;
            Object actualRuntime = i.getEntity();
            Set<String> allowed = i.getRule().allowedRuntimes();
            logManager.error(payload.getProcessKey(), MessageFormat.format(Objects.requireNonNull(msg), actualRuntime, allowed));
        });

        if (!result.getDeny().isEmpty()) {
            throw new ProcessException(payload.getProcessKey(), "'configuration.runtime' value policy violation");
        }
    }
}
