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

import java.util.Objects;
import java.util.concurrent.Callable;

public class JsonStorePolicy {

    private final JsonStoreRule rule;

    public JsonStorePolicy(JsonStoreRule rule) {
        this.rule = rule;
    }

    public CheckResult<JsonStoreRule.StoreRule, Integer> checkStorage(Callable<Integer> storageCount) throws Exception {
        if (rule == null || rule.store() == null) {
            return CheckResult.success();
        }

        JsonStoreRule.StoreRule store = Objects.requireNonNull(rule.store());

        int currentCount = storageCount.call();
        if (currentCount >= store.maxNumberPerOrg()) {
            return CheckResult.error(new CheckResult.Item<>(store, currentCount));
        }
        return CheckResult.success();
    }

    public CheckResult<JsonStoreRule.StoreDataRule, Long> checkStorageData(Callable<Long> currentStorageSize) throws Exception {
        if (rule == null || rule.data() == null) {
            return CheckResult.success();
        }

        JsonStoreRule.StoreDataRule data = Objects.requireNonNull(rule.data());

        long current = currentStorageSize.call();
        if (current >= data.maxSizeInBytes()) {
            return CheckResult.error(new CheckResult.Item<>(data, current));
        }
        return CheckResult.success();
    }

    public Long getMaxSize() {
        if (rule == null || rule.data() == null) {
            return null;
        }

        return Objects.requireNonNull(rule.data()).maxSizeInBytes();
    }
}
