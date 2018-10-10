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

import com.walmartlabs.concord.sdk.Context;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.policyengine.Utils.matches;

public class TaskPolicy {

    private final PolicyRules<TaskRule> rules;

    public TaskPolicy(PolicyRules<TaskRule> rules) {
        this.rules = rules;
    }

    public CheckResult<TaskRule, String> check(String taskName, String methodName, Object[] params) {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        for(TaskRule r : rules.getAllow()) {
            if (matchRule(taskName, methodName, params, r)) {
                return CheckResult.success();
            }
        }

        for(TaskRule r : rules.getDeny()) {
            if (matchRule(taskName, methodName, params, r)) {
                return new CheckResult<>(Collections.emptyList(), Collections.singletonList(new CheckResult.Item<>(r, methodName)));
            }
        }

        for(TaskRule r : rules.getWarn()) {
            if (matchRule(taskName, methodName, params, r)) {
                return new CheckResult<>(Collections.singletonList(new CheckResult.Item<>(r, methodName)), Collections.emptyList());
            }
        }

        return CheckResult.success();
    }

    private boolean matchRule(String taskName, String methodName, Object[] params, TaskRule r) {
        if (!matches(r.getTaskName(), taskName)) {
            return false;
        }

        if (r.getMethod() != null && !matches(r.getMethod(), methodName)) {
            return false;
        }

        if (!paramsMatches(r.getParams(), params)) {
            return false;
        }

        return true;
    }

    private boolean paramsMatches(List<TaskRule.Param> r, Object[] params) {
        if (params == null) {
            return false;
        }

        for (TaskRule.Param p : r) {
            if (p.getIndex() >= params.length) {
                return false;
            }

            if (!paramMatches(
                    Optional.ofNullable(p.getName()).map(n -> n.split("\\.")).orElse(null),
                    0,
                    p.getValues(), params[p.getIndex()])) {
                return false;
            }
        }

        return true;
    }

    @SuppressWarnings("unchecked")
    private static boolean paramMatches(String[] names, int nameIndex, List<Object> values, Object param) {
        if (param == null) {
            return false;
        }

        if (param instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) param;
            String name = names[nameIndex];
            nameIndex += 1;
            return paramMatches(names, nameIndex, values, m.get(name));
        } else if (param instanceof Context) {
            Context ctx = (Context) param;
            String name = names[nameIndex];
            nameIndex += 1;
            return paramMatches(names, nameIndex, values, ctx.getVariable(name));
        } else if (param instanceof String) {
            return Utils.matchAny(values.stream().map(Object::toString).collect(Collectors.toList()), param.toString());
        } else {
            for (Object v : values) {
                if (v.equals(param)) {
                    return true;
                }
            }
        }

        return false;
    }
}
