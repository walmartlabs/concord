package com.walmartlabs.concord.bootstrap.metrics;

import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.codahale.metrics.jvm.*;

import javax.management.MBeanServer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class JvmMetricSet implements MetricSet {

    private final MBeanServer server;

    public JvmMetricSet(MBeanServer server) {
        this.server = server;
    }

    @Override
    public Map<String, Metric> getMetrics() {
        Map<String, Metric> metrics = new HashMap<>();

        metrics.put("buffer-pool", new BufferPoolMetricSet(server));
        metrics.put("class-loading", new ClassLoadingGaugeSet());
        metrics.put("fd", new FileDescriptorRatioGauge());
        metrics.put("gc", new GarbageCollectorMetricSet());
        metrics.put("memory-usage", new MemoryUsageGaugeSet());
        metrics.put("thread-states", new ThreadStatesGaugeSet());

        return Collections.unmodifiableMap(metrics);
    }
}
