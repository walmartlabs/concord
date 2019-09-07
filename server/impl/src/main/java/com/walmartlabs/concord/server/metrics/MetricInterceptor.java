package com.walmartlabs.concord.server.metrics;

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

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.base.Suppliers;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import javax.inject.Provider;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class MetricInterceptor implements MethodInterceptor {

    private final Map<Method, String> timerNameCache = new HashMap<>();

    @Inject
    private Provider<MetricRegistry> registryProvider;

    // because we had to use very late binding for MetricRegistry, let's cache the result
    private Supplier<MetricRegistry> registrySupplier = Suppliers.memoize(() -> this.registryProvider.get());

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        MetricRegistry registry = registrySupplier.get();

        Timer t = registry.timer(timerName(invocation.getMethod()));
        Timer.Context ctx = t.time();
        try {
            return invocation.proceed();
        } finally {
            ctx.stop();
        }
    }

    private String timerName(Method m) {
        return timerNameCache.computeIfAbsent(m, k -> {
            WithTimer t = m.getAnnotation(WithTimer.class);
            return MetricUtils.createFqn("timer", m.getDeclaringClass(), m.getName(), t.suffix());
        });
    }
}
