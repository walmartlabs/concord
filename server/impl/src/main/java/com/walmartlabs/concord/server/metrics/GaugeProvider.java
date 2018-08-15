package com.walmartlabs.concord.server.metrics;

import com.codahale.metrics.Gauge;

public interface GaugeProvider<T> {

    String name();

    Gauge<T> gauge();
}
