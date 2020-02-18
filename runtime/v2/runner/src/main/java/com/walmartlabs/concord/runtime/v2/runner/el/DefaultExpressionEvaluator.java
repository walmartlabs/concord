package com.walmartlabs.concord.runtime.v2.runner.el;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.runner.tasks.TaskProvider;
import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.svm.Runtime;
import org.eclipse.sisu.Typed;

import javax.el.*;
import javax.inject.Named;

@Named
@Typed
public class DefaultExpressionEvaluator implements ExpressionEvaluator {

    // TODO deprecate "execution"? what about scripts - they can't use "context"?
    private static final String[] CONTEXT_VARIABLE_NAMES = {Constants.Context.CONTEXT_KEY, "execution"};

    private final ExpressionFactory expressionFactory = ExpressionFactory.newInstance();

    @Override
    public <T> T eval(Context ctx, String expr, Class<T> type) {
        ELResolver r = createResolver(ctx);

        StandardELContext sc = new StandardELContext(expressionFactory);
        sc.putContext(ExpressionFactory.class, expressionFactory);
        sc.addELResolver(r);

        // save the context as a variable
        VariableMapper vm = sc.getVariableMapper();
        for (String k : CONTEXT_VARIABLE_NAMES) {
            vm.setVariable(k, expressionFactory.createValueExpression(ctx, Context.class));
        }

        ValueExpression x = expressionFactory.createValueExpression(sc, expr, type);
        try {
            Object v = x.getValue(sc);
            return type.cast(v);
        } catch (PropertyNotFoundException e) {
            throw new RuntimeException("Can't find a variable in '" + expr + "'. Check if it is defined in the current scope. Details: " + e.getMessage());
        }
    }

    private ELResolver createResolver(Context ctx) {
        CompositeELResolver composite = new CompositeELResolver();
        composite.add(new InjectVariableResolver());
        composite.add(new GlobalVariableResolver(ctx.globalVariables()));


        Runtime rt = ctx.execution().runtime();
        TaskProvider taskProvider = rt.getService(TaskProvider.class);
        composite.add(new TaskResolver(taskProvider));

        return composite;
    }
}
