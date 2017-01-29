package com.walmartlabs.concord.bootstrap.metrics;

import com.codahale.metrics.JmxReporter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

public class Metrics {

    private static final Logger log = LoggerFactory.getLogger(Metrics.class);
    private static final String REGISTRY_NAME = "main";
    private static final String METRICS_NAME = "jvm";

    private final String graphiteHost;
    private final int graphitePort;
    private final String graphitePrefix;

    private MetricRegistry metricRegistry;
    private JmxReporter jmxReporter;
    private GraphiteReporter graphiteReporter;

    public Metrics(String graphiteHost, int graphitePort, String graphitePrefix) {
        this.graphiteHost = graphiteHost;
        this.graphitePort = graphitePort;
        this.graphitePrefix = graphitePrefix;
    }

    public synchronized void start() {
        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        metricRegistry = SharedMetricRegistries.getOrCreate(REGISTRY_NAME);
        metricRegistry.register(METRICS_NAME, new JvmMetricSet(server));

        String metricsDomain = "main";
        jmxReporter = JmxReporter.forRegistry(metricRegistry).inDomain(metricsDomain).build();
        jmxReporter.start();
        log.info("JMX reporter started");

        if (graphiteHost != null) {
            Graphite graphite = new Graphite(new InetSocketAddress(graphiteHost, graphitePort));
            graphiteReporter = GraphiteReporter.forRegistry(metricRegistry)
                    .prefixedWith(graphitePrefix)
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .filter(MetricFilter.ALL)
                    .build(graphite);
            graphiteReporter.start(1, TimeUnit.MINUTES);
            log.info("Graphite reporter started");
        }
    }

    public synchronized void stop() {
        if (graphiteReporter != null) {
            graphiteReporter.stop();
            log.info("Graphite reporter stopped");
            graphiteReporter = null;
        }

        if (jmxReporter != null) {
            jmxReporter.stop();
            log.info("JMX reporter stopped");
            jmxReporter = null;
        }

        SharedMetricRegistries.remove(REGISTRY_NAME);
        if (metricRegistry != null) {
            metricRegistry.remove(METRICS_NAME);
        }
    }
}
