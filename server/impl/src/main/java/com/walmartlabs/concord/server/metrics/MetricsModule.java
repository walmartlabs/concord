package com.walmartlabs.concord.server.metrics;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;

public class MetricsModule extends AbstractModule {

    @Override
    protected void configure() {
        bindInterceptor(Matchers.any(), Matchers.annotatedWith(WithTimer.class), new MetricsInterceptor());
    }
}
