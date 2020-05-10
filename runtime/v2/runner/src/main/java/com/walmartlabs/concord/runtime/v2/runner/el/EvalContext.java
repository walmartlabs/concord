package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Expression evaluation context.
 */
public interface EvalContext {

    /**
     * Required if expressions call other tasks.
     */
    @Nullable
    Context context();

    /**
     * Variables that will be made available during the evaluation.
     * @see #useIntermediateResults()
     */
    @Value.Default
    default Map<String, Object> variables() {
        return Collections.emptyMap();
    }

    /**
     * If {@code true} then intermediate results will be available during
     * the evaluation.
     */
    @Value.Default
    default boolean useIntermediateResults() {
        return false;
    }

    /**
     * If {@code true} then undefined variables will be resolved to
     * {@code null} instead of throwing an exception.
     */
    @Value.Default
    default boolean undefinedVariableAsNull() {
        return false;
    }
}
