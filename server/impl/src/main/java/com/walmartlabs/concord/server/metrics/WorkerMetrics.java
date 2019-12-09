package com.walmartlabs.concord.server.metrics;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.server.AgentWorkerUtils;
import com.walmartlabs.concord.server.agent.AgentManager;
import com.walmartlabs.concord.server.agent.AgentWorkerEntry;
import com.walmartlabs.concord.server.cfg.WorkerMetricsConfiguration;
import com.walmartlabs.ollie.lifecycle.Lifecycle;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;
import org.eclipse.sisu.EagerSingleton;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Named
@EagerSingleton
public class WorkerMetrics implements Lifecycle {

    private final Collector collector;

    @Inject
    public WorkerMetrics(WorkerMetricsConfiguration cfg, AgentManager agentManager) {
        String prop = cfg.getGroupByCapabilitiesProperty();
        String[] path = prop.split("\\.");

        collector = new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                Collection<AgentWorkerEntry> data = agentManager.getAvailableAgents();
                Map<Object, Long> m = AgentWorkerUtils.groupBy(data, path);

                GaugeMetricFamily f = new GaugeMetricFamily("available_workers", "number of available workers for the " + prop, Collections.singletonList("prop"));
                m.forEach((k, v) -> {
                    String label = k.toString().replace("/", "_").replace("-", "_");
                    f.addMetric(Collections.singletonList(label), v);
                });

                return Collections.singletonList(f);
            }
        };
    }

    @Override
    public void start() {
        CollectorRegistry.defaultRegistry.register(collector);
    }

    @Override
    public void stop() {
        CollectorRegistry.defaultRegistry.unregister(collector);
    }
}
