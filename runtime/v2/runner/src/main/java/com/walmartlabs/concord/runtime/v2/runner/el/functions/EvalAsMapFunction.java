package com.walmartlabs.concord.runtime.v2.runner.el.functions;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v2.runner.el.ThreadLocalEvalContext;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;
import com.walmartlabs.concord.svm.Runtime;

import java.util.HashMap;
import java.util.Map;

import static com.walmartlabs.concord.common.ConfigurationUtils.deepMerge;

public final class EvalAsMapFunction {

    @ELFunction
    @SuppressWarnings("unchecked")
    public static Map<String, Object> evalAsMap(Object value) {
        Context ctx = ThreadLocalEvalContext.get().context();
        if (ctx == null) {
            return null;
        }

        Runtime runtime = ctx.execution().runtime();
        EvalContextFactory ecf = runtime.getService(EvalContextFactory.class);
        ExpressionEvaluator ee = runtime.getService(ExpressionEvaluator.class);
        Map<String, Object> evalValue = ee.evalAsMap(ecf.scope(ctx), value);

        Map<String, Object> result = new HashMap<>();
        evalValue.forEach((k, v) -> {
            Object o = ctx.variables().get(k);
            if (o instanceof Map && v instanceof Map) {
                v = deepMerge((Map<String, Object>)o, (Map<String, Object>)v);
            }
            if (v != null) {
                result.put(k, v);
            }
        });
        return result;
    }
}
