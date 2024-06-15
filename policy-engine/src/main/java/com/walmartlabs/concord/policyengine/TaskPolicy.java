package com.walmartlabs.concord.policyengine;

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

import com.walmartlabs.concord.runtime.v2.sdk.Variables;
import com.walmartlabs.concord.sdk.Context;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.policyengine.Utils.matches;

public class TaskPolicy {

    private final PolicyRules<TaskRule> rules;

    public TaskPolicy(PolicyRules<TaskRule> rules) {
        this.rules = rules;
    }

    public CheckResult<TaskRule, String> check(String taskName, String methodName, Object[] params, Map<String, List<Serializable>> taskResults) {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        for (TaskRule r : rules.getAllow()) {
            if (matchRule(taskName, methodName, params, taskResults, r)) {
                return CheckResult.success();
            }
        }

        for (TaskRule r : rules.getDeny()) {
            if (matchRule(taskName, methodName, params, taskResults, r)) {
                return CheckResult.error(new CheckResult.Item<>(r, methodName));
            }
        }

        for (TaskRule r : rules.getWarn()) {
            if (matchRule(taskName, methodName, params, taskResults, r)) {
                return CheckResult.warn(new CheckResult.Item<>(r, methodName));
            }
        }

        return CheckResult.success();
    }

    public Set<String> getTaskResults() {
        if (rules == null || rules.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> result = new HashSet<>();
        rules.getAllow().forEach(r -> collectTaskNames(r.taskResults(), result));
        rules.getDeny().forEach(r -> collectTaskNames(r.taskResults(), result));
        rules.getWarn().forEach(r -> collectTaskNames(r.taskResults(), result));
        return result;
    }

    private static void collectTaskNames(List<TaskRule.TaskResult> taskResults, Set<String> result) {
        for (TaskRule.TaskResult tr : taskResults) {
            result.add(tr.task());
        }
    }

    private boolean matchRule(String taskName, String methodName, Object[] params, Map<String, List<Serializable>> taskResults, TaskRule r) {
        if (!matches(r.taskName(), taskName)) {
            return false;
        }

        if (r.method() != null && !matches(r.method(), methodName)) {
            return false;
        }

        if (paramsMatches(r.params(), params)) {
            return true;
        }

        if (taskResultsMatches(r.taskResults(), taskResults)) {
            return true;
        }

        return false;
    }

    private static boolean paramsMatches(List<TaskRule.Param> r, Object[] params) {
        if (params == null) {
            return r.isEmpty();
        }

        for (TaskRule.Param p : r) {
            if (p.index() >= params.length) {
                return false;
            }

            if (!paramMatches(
                    Optional.ofNullable(p.name()).map(n -> n.split("\\.")).orElse(null),
                    0,
                    p.values(), params[p.index()],
                    p.protectedVariable())) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean paramMatches(String[] names, int nameIndex, List<Object> values, Object param, boolean isProtected) {
        if (param == null) {
            return values.contains(null);
        }

        if (param instanceof Map) {
            if (names == null) {
                return false;
            }
            Map<String, Object> m = (Map<String, Object>) param;
            String name = names[nameIndex];
            nameIndex += 1;
            return paramMatches(names, nameIndex, values, m.get(name), isProtected);
        } else if (param instanceof Context) {
            if (names == null) {
                return false;
            }
            Context ctx = (Context) param;
            String name = names[nameIndex];
            nameIndex += 1;
            Object v = isProtected ? ctx.getProtectedVariable(name) : ctx.getVariable(name);
            return paramMatches(names, nameIndex, values, v, isProtected);
        } else if (param instanceof Variables) {
            Variables vars = (Variables) param;
            String name = names[nameIndex];
            nameIndex += 1;
            Object v = vars.get(name);
            return paramMatches(names, nameIndex, values, v, isProtected);
        } else if (param instanceof com.walmartlabs.concord.runtime.v2.sdk.Context) {
            com.walmartlabs.concord.runtime.v2.sdk.Context ctx = (com.walmartlabs.concord.runtime.v2.sdk.Context) param;
            String name = names[nameIndex];
            nameIndex += 1;
            Object v = ctx.variables().get(name);
            return paramMatches(names, nameIndex, values, v, isProtected);
        } else if (param instanceof String) {
            return Utils.matchAny(values.stream().map(Object::toString).collect(Collectors.toList()), param.toString());
        } else {
            for (Object v : values) {
                if (v != null && v.equals(param)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean taskResultsMatches(List<TaskRule.TaskResult> rule, Map<String, List<Serializable>> taskResults) {
        if (rule.isEmpty() || taskResults == null) {
            return false;
        }

        for (TaskRule.TaskResult tr : rule) {
            String taskName = tr.task();
            List<Serializable> results = taskResults.getOrDefault(taskName, Collections.emptyList());
            String resultName = tr.result();

            for (Object result : results) {
                if (paramMatches(
                        Optional.ofNullable(resultName).map(n -> n.split("\\.")).orElse(null),
                        0, tr.values(), result, false)) {
                    return true;
                }
            }
        }

        return false;
    }
}
