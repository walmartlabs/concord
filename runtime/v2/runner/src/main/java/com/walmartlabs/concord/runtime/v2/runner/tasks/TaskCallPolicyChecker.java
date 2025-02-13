package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.walmartlabs.concord.policyengine.CheckResult;
import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.policyengine.TaskRule;
import com.walmartlabs.concord.runtime.v2.runner.TaskResultService;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

public class TaskCallPolicyChecker implements TaskCallListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCallPolicyChecker.class);

    private final PolicyEngine policyEngine;
    private final TaskResultService taskResultService;

    @Inject
    public TaskCallPolicyChecker(PolicyEngine policyEngine, TaskResultService taskResultService) {
        this.policyEngine = policyEngine;
        this.taskResultService = taskResultService;
    }

    @Override
    public void onEvent(TaskCallEvent event) {
        if (event.phase() == TaskCallEvent.Phase.POST) {
            return;
        }

        CheckResult<TaskRule, String> result = policyEngine.getTaskPolicy().check(
                event.taskName(),
                event.methodName(),
                event.input().toArray(),
                taskResultService.getResults());

        result.getWarn().forEach(d -> log.warn("Potentially restricted task call '{}' (task policy {})", event.taskName(), d.getRule()));
        result.getDeny().forEach(d -> log.error("Task call '{}' is forbidden by the task policy {}", event.taskName(), d.getRule()));

        if (!result.getDeny().isEmpty()) {
            throw new UserDefinedException("Found forbidden tasks");
        }
    }
}
