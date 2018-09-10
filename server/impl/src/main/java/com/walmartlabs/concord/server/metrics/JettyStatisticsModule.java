package com.walmartlabs.concord.server.metrics;

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

import javax.inject.Named;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

@Named
public class JettyStatisticsModule extends AbstractModule {

    private static final String[] ATTRIBUTES = {
            "responses1xx",
            "responses2xx",
            "responses3xx",
            "responses4xx",
            "responses5xx",

            "requestsActive",
            "requestTimeMax",
            "requestTimeMean"
    };

    @Override
    protected void configure() {
        Multibinder<GaugeProvider> tasks = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        for (String a : ATTRIBUTES) {
            tasks.addBinding().toInstance(attribute(a));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> GaugeProvider<T> attribute(String attribute) {
        return new GaugeProvider<T>() {
            @Override
            public String name() {
                return "jetty-" + attribute;
            }

            @Override
            public Gauge<T> gauge() {
                return () -> (T) getAttribute(attribute);
            }
        };
    }

    private static Object getAttribute(String attribute) {
        try {
            MBeanServer mBeans = ManagementFactory.getPlatformMBeanServer();
            return mBeans.getAttribute(new ObjectName("org.eclipse.jetty.server.handler:type=statisticshandler,id=0"), attribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
