package com.walmartlabs.concord.policyengine;

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

import java.util.function.Supplier;

public class KvPolicy {

    private final KvRule rule;

    public KvPolicy(KvRule rule) {
        this.rule = rule;
    }

    public CheckResult<KvRule, Integer> check(
            Supplier<Integer> currentEntriesCount,
            Supplier<Boolean> exists) {

        if (rule == null) {
            return CheckResult.success();
        }

        Integer count = currentEntriesCount.get();
        if (count == null) {
            count = 0;
        }

        if (rule.maxEntries() >= count + 1) {
            return CheckResult.success();
        }

        if (exists.get() && rule.maxEntries() >= count) {
            return CheckResult.success();
        }

        return CheckResult.error(new CheckResult.Item<>(rule, count));
    }

    public Integer getMaxEntries() {
        if (rule == null) {
            return null;
        }

        return rule.maxEntries();
    }
}
