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
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import javax.inject.Inject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class MetricInterceptor implements MethodInterceptor {

    private static final String SHARED_PREFIX = "com.walmartlabs.concord.";

    private final Map<Method, String> timerNameCache = new HashMap<>();

    @Inject
    private MetricRegistry registry;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
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
            String n = m.getDeclaringClass().getName();
            if (n.startsWith(SHARED_PREFIX)) {
                n = n.substring(SHARED_PREFIX.length());
            }
            WithTimer t = m.getAnnotation(WithTimer.class);
            return "timer," + n + "." + m.getName() + t.suffix();
        });
    }
}
