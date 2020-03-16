package com.walmartlabs.concord.runner.engine;

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

import com.walmartlabs.concord.policyengine.PolicyEngine;
import com.walmartlabs.concord.sdk.Context;

import java.util.UUID;

public class ProtectedVarContext implements TaskInterceptor {

    private static final ThreadLocal<UUID> threadLocalScope = new ThreadLocal<>();

    private static final UUID token = UUID.randomUUID();

    private final PolicyEngine policyEngine;

    public ProtectedVarContext(PolicyEngine policyEngine) {
        this.policyEngine = policyEngine;
    }

    @Override
    public void preTask(String taskName, Object instance, Context ctx) {
        if (policyEngine != null && policyEngine.getProtectedTasksPolicy().isProtected(taskName)) {
            threadLocalScope.set(token);
        }
    }

    @Override
    public void postTask(String taskName, Object instance, Context ctx) {
        threadLocalScope.remove();
    }

    public boolean hasToken() {
        return threadLocalScope.get() != null;
    }
}
