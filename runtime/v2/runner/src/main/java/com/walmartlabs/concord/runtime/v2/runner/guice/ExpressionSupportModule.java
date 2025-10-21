package com.walmartlabs.concord.runtime.v2.runner.guice;

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

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.runtime.v2.runner.el.DefaultExpressionEvaluator;
import com.walmartlabs.concord.runtime.v2.runner.el.FunctionHolder;
import com.walmartlabs.concord.runtime.v2.runner.el.functions.*;
import com.walmartlabs.concord.runtime.v2.sdk.ELFunction;
import com.walmartlabs.concord.runtime.v2.sdk.ExpressionEvaluator;

import java.lang.reflect.Modifier;

public class ExpressionSupportModule implements Module {

    @Override
    public void configure(Binder binder) {
        binder.bind(ExpressionEvaluator.class).to(DefaultExpressionEvaluator.class);

        // support for @ELFunction
        var functionHolder = new FunctionHolder();
        binder.bind(FunctionHolder.class).toInstance(functionHolder);
        binder.bindListener(Matchers.any(), new TypeListener() {
            @Override
            public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                var clazz = type.getRawType();

                for (var method : clazz.getDeclaredMethods()) {
                    var annotation = method.getAnnotation(ELFunction.class);
                    if (annotation == null) {
                        continue;
                    }

                    if (!Modifier.isStatic(method.getModifiers())) {
                        var msg = String.format("@ELFunction method must be static: %s.%s", clazz.getName(), method.getName());
                        encounter.addError(msg);
                        continue;
                    }

                    if (!Modifier.isPublic(method.getModifiers())) {
                        var msg = String.format("@ELFunction method must be public: %s.%s", clazz.getName(), method.getName());
                        encounter.addError(msg);
                        continue;
                    }

                    var name = annotation.value();
                    if (name == null || name.isBlank()) {
                        name = method.getName();
                    }
                    functionHolder.register(name, method);
                }
            }
        });

        // built-in functions
        binder.bind(AllVariablesFunction.class).asEagerSingleton();
        binder.bind(CurrentFlowNameFunction.class).asEagerSingleton();
        binder.bind(EvalAsMapFunction.class).asEagerSingleton();
        binder.bind(HasFlowFunction.class).asEagerSingleton();
        binder.bind(HasNonNullVariableFunction.class).asEagerSingleton();
        binder.bind(HasVariableFunction.class).asEagerSingleton();
        binder.bind(IsDebugFunction.class).asEagerSingleton();
        binder.bind(IsDryRunFunction.class).asEagerSingleton();
        binder.bind(MaskFunction.class).asEagerSingleton();
        binder.bind(OrDefaultFunction.class).asEagerSingleton();
        binder.bind(ThrowFunction.class).asEagerSingleton();
        binder.bind(UuidFunction.class).asEagerSingleton();
    }
}
