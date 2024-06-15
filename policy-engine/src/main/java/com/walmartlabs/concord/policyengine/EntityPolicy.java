package com.walmartlabs.concord.policyengine;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.walmartlabs.concord.policyengine.Utils.matches;

public class EntityPolicy {

    private final PolicyRules<EntityRule> rules;

    public EntityPolicy(PolicyRules<EntityRule> rules) {
        this.rules = rules;
    }

    public CheckResult<EntityRule, Map<String, Object>> check(String entity, String action, Supplier<Map<String, Object>> attrs) {
        if (rules == null || rules.isEmpty()) {
            return CheckResult.success();
        }

        List<CheckResult.Item<EntityRule, Map<String, Object>>> warn = new ArrayList<>();
        List<CheckResult.Item<EntityRule, Map<String, Object>>> deny = new ArrayList<>();

        check(entity, action, attrs.get(), warn, deny);

        return new CheckResult<>(warn, deny);
    }

    private void check(String entity, String action, Map<String, Object> attrs,
                       List<CheckResult.Item<EntityRule, Map<String, Object>>> warn,
                       List<CheckResult.Item<EntityRule, Map<String, Object>>> deny) {

        for (EntityRule r : rules.getAllow()) {
            if (matchRule(r, entity, action, attrs)) {
                return;
            }
        }

        for (EntityRule r : rules.getDeny()) {
            if (matchRule(r, entity, action, attrs)) {
                deny.add(new CheckResult.Item<>(r, attrs));
                return;
            }
        }

        for (EntityRule r : rules.getWarn()) {
            if (matchRule(r, entity, action, attrs)) {
                warn.add(new CheckResult.Item<>(r, attrs));
                return;
            }
        }
    }

    private boolean matchRule(EntityRule r, String entity, String action, Map<String, Object> attrs) {
        if (r.entity() != null && !matches(r.entity(), entity)) {
            return false;
        }

        if (r.action() != null && !matches(r.action(), action)) {
            return false;
        }

        if (r.conditions() != null && !matches(r.conditions(), attrs)) {
            return false;
        }

        return true;
    }
}
