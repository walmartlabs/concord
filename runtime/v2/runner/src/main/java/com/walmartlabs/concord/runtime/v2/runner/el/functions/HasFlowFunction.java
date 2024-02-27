package com.walmartlabs.concord.runtime.v2.runner.el.functions;

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

import com.walmartlabs.concord.runtime.v2.model.ProcessDefinition;
import com.walmartlabs.concord.runtime.v2.model.Profile;
import com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;

import java.lang.reflect.Method;

public final class HasFlowFunction {

    public static Method getMethod() {
        try {
            return HasFlowFunction.class.getMethod("hasFlow", String.class);
        } catch (Exception e) {
            throw new RuntimeException("Method not found");
        }
    }

    public static boolean hasFlow(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }

        Context ctx = ThreadLocalEvalContext.get().context();
        if (ctx == null) {
            return false;
        }

        ProcessDefinition pd = ctx.execution().processDefinition();
        if (pd.flows().containsKey(name)) {
            return true;
        }

        for (String activeProfile : ctx.processConfiguration().processInfo().activeProfiles()) {
            boolean fromProfile = pd.profiles().getOrDefault(activeProfile, Profile.builder().build()).flows().containsKey(name);
            if (fromProfile) {
                return true;
            }
        }
        return false;
    }
}
