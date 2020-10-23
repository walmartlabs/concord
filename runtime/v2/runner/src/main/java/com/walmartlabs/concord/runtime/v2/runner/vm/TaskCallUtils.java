package com.walmartlabs.concord.runtime.v2.runner.vm;

import com.walmartlabs.concord.runtime.v2.model.TaskCallOptions;
import com.walmartlabs.concord.runtime.v2.runner.el.EvalContextFactory;
import com.walmartlabs.concord.runtime.v2.runner.el.ExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import com.walmartlabs.concord.svm.Runtime;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class TaskCallUtils {

    public static void processOut(TaskResult result, TaskCallOptions opts, Context ctx, Runtime runtime) {
        Objects.requireNonNull(result);

        if (result.suspended()) {
            return;
        }

        if (opts.out() != null) {
            ctx.variables().set(opts.out(), result.toMap());
        } else if (opts.outExpr() != null) {
            ExpressionEvaluator expressionEvaluator = runtime.getService(ExpressionEvaluator.class);

            Map<String, Object> vars = Collections.singletonMap("result", result.toMap());
            Map<String, Serializable> out = expressionEvaluator.evalAsMap(EvalContextFactory.global(ctx, vars), opts.outExpr());
            out.forEach((k, v) -> ctx.variables().set(k, v));
        }
    }

    private TaskCallUtils() {
    }
}
