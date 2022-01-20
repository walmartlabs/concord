package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2022 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.svm.Runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public final class OutputUtils {

    public static void process(Runtime runtime, Context ctx, Map<String, Object> result, String out, Map<String, Serializable> outExpr) {
        if (out != null) {
            ctx.variables().set(out, result);
        } else if (!outExpr.isEmpty()) {
            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);
            Map<String, Object> vars = Collections.singletonMap("result", result);
            Map<String, Serializable> evalOut = expressionEvaluator.evalAsMap(EvalContextFactory.global(ctx, vars), outExpr);
            evalOut.forEach((k, v) -> ctx.variables().set(k, v));
        }
    }

    private OutputUtils() {
    }
}
