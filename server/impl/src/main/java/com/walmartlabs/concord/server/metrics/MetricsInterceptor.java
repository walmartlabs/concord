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

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import java.lang.reflect.Method;

public class MetricsInterceptor implements MethodInterceptor {

    private static final String SHARED_PREFIX = "com.walmartlabs.concord.";

    private final MetricRegistry metrics;

    public MetricsInterceptor() {
        this.metrics = new MetricRegistry();

        JmxReporter reporter = JmxReporter.forRegistry(metrics).build();
        reporter.start();
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Timer t = metrics.timer(timerName(invocation.getMethod()));
        Timer.Context ctx = t.time();
        try {
            return invocation.proceed();
        } finally {
            ctx.stop();
        }
    }

    private static String timerName(Method m) {
        String n = m.getDeclaringClass().getName();
        if (n.startsWith(SHARED_PREFIX)) {
            n = n.substring(SHARED_PREFIX.length());
        }
        return "timer," + n + "." + m.getName();
    }
}
