package com.walmartlabs.concord.server.metrics;

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
            Object result = invocation.proceed();
            return result;
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
