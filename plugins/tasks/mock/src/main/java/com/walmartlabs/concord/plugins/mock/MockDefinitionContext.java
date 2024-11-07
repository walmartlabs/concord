package com.walmartlabs.concord.plugins.mock;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.model.Step;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.util.Objects;

public record MockDefinitionContext(Step currentStep, String taskName, Variables input, String method, Object[] params) {

    public static MockDefinitionContext task(Step currentStep, String taskName, Variables input) {
        Objects.requireNonNull(taskName);
        Objects.requireNonNull(input);

        return new MockDefinitionContext(currentStep, taskName, input, null, null);
    }

    public static MockDefinitionContext method(Step currentStep, String taskName, String methodName, Object[] params) {
        Objects.requireNonNull(taskName);
        Objects.requireNonNull(methodName);
        Objects.requireNonNull(params);
        return new MockDefinitionContext(currentStep, taskName, null, methodName, params);
    }
}
