package com.walmartlabs.concord.runtime.v25.runner.task;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaskPolicyHook implements TaskRuntime.TaskHook {

    private static final Logger log = LoggerFactory.getLogger(TaskPolicyHook.class);

    private final PolicyEngine policyEngine;

    public TaskPolicyHook(PolicyEngine policyEngine) {
        this.policyEngine = Objects.requireNonNull(policyEngine, "policyEngine");
    }

    @Override
    public int order() {
        return -100;
    }

    @Override
    public void before(TaskRuntime.Invocation invocation) {
        var policy = policyEngine.getTaskPolicy();
        var result = policy.check(invocation.taskName(), invocation.methodName(), invocation.arguments().toArray(),
                priorResults(invocation.history(), policy.getTaskResults()));
        result.getWarn().forEach(match -> log.warn("Potentially restricted task call '{}.{}' (task policy {})",
                invocation.taskName(), invocation.methodName(), match.getRule()));
        if (!result.getDeny().isEmpty()) {
            var rules = result.getDeny().stream().map(match -> match.getRule().toString()).toList();
            throw new UserDefinedException("Task call '" + invocation.taskName() + "." + invocation.methodName()
                    + "' is forbidden by task policy " + rules);
        }
    }

    private static Map<String, List<Serializable>> priorResults(List<TaskRuntime.HistoryEntry> history,
                                                                 java.util.Set<String> collectedTasks) {
        if (collectedTasks.isEmpty() || history.isEmpty()) {
            return Map.of();
        }
        var result = new LinkedHashMap<String, List<Serializable>>();
        for (var entry : history) {
            if (!entry.successful() || !collectedTasks.contains(entry.taskName())
                    || !(entry.result() instanceof Serializable value)) {
                continue;
            }
            result.computeIfAbsent(entry.taskName(), ignored -> new ArrayList<>()).add(value);
        }
        return result;
    }
}
