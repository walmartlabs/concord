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

import java.util.concurrent.Callable;

public class JsonStorePolicy {

    private final JsonStoreRule rule;

    public JsonStorePolicy(JsonStoreRule rule) {
        this.rule = rule;
    }

    public CheckResult<JsonStoreRule.StoreRule, Integer> checkStorage(Callable<Integer> storageCount) throws Exception {
        if (rule == null || rule.getStore() == null) {
            return CheckResult.success();
        }

        int currentCount = storageCount.call();
        if (currentCount >= rule.getStore().getMaxNumberPerOrg()) {
            return CheckResult.error(new CheckResult.Item<>(rule.getStore(), currentCount));
        }
        return CheckResult.success();
    }

    public CheckResult<JsonStoreRule.StoreDataRule, Long> checkStorageData(Callable<Long> currentStorageSize) throws Exception {
        if (rule == null || rule.getData() == null) {
            return CheckResult.success();
        }

        long current = currentStorageSize.call();
        if (current >= rule.getData().getMaxSizeInBytes()) {
            return CheckResult.error(new CheckResult.Item<>(rule.getData(), current));
        }
        return CheckResult.success();
    }

    public Long getMaxSize() {
        if (rule == null || rule.getData() == null) {
            return null;
        }

        return rule.getData().getMaxSizeInBytes();
    }
}
