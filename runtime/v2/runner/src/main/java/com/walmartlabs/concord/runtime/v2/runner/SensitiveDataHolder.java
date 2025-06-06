package com.walmartlabs.concord.runtime.v2.runner;

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

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class SensitiveDataHolder implements com.walmartlabs.concord.runtime.v2.sdk.SensitiveDataHolder {

    private static final SensitiveDataHolder INSTANCE = new SensitiveDataHolder();

    @Deprecated
    public static SensitiveDataHolder getInstance() {
        return INSTANCE;
    }

    private final Set<String> sensitiveData = new CopyOnWriteArraySet<>();

    @Override
    public Set<String> get() {
        return sensitiveData;
    }

    @Override
    public void add(String sensitiveData) {
        if (sensitiveData == null || sensitiveData.isBlank()) {
            return;
        }

        this.sensitiveData.add(sensitiveData);
    }

    @Override
    public void addAll(Collection<String> sensitiveData) {
        this.sensitiveData.addAll(sensitiveData);
    }
}
