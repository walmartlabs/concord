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

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public class ConcurrentProcessPolicy {

    private final ConcurrentProcessRule rule;

    public ConcurrentProcessPolicy(ConcurrentProcessRule rule) {
        this.rule = rule;
    }

    public CheckResult<ConcurrentProcessRule, List<UUID>> check(
            Supplier<List<UUID>> processPerOrg,
            Supplier<List<UUID>> processPerProject) {

        if (rule == null || (rule.maxPerOrg() == null && rule.maxPerProject() == null)) {
            return CheckResult.success();
        }

        int max;
        List<UUID> processes;
        if (rule.maxPerOrg() != null) {
            processes = processPerOrg.get();
            max = Objects.requireNonNull(rule.maxPerOrg());
        } else {
            processes = processPerProject.get();
            max = Objects.requireNonNull(rule.maxPerProject());
        }

        if (processes.size() >= max) {
            return CheckResult.error(new CheckResult.Item<>(rule, processes));
        }
        return CheckResult.success();
    }
}
