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
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.hotspot.DefaultExports;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Set;

@Named
@Singleton
public class MetricRegistryProvider implements Provider<MetricRegistry> {

    private final Set<GaugeProvider<?>> gauges;

    @Inject
    public MetricRegistryProvider(Set<GaugeProvider<?>> gauges) {
        this.gauges = gauges;
    }

    @Override
    public MetricRegistry get() {
        MetricRegistry registry = new MetricRegistry();

        // prometheus integration
        CollectorRegistry.defaultRegistry.register(new DropwizardExports(registry));

        // initialize standard prometheus exports (hotspot, memory, etc)
        DefaultExports.initialize();

        // register gauges
        gauges.forEach(g -> registry.register(g.name(), g.gauge()));

        return registry;
    }
}
