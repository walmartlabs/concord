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
import com.walmartlabs.concord.server.sdk.BackgroundTask;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class WorkerMetrics implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(WorkerMetrics.class);

    private final Collector collector;

    @Inject
    public WorkerMetrics(WorkerMetricsConfiguration cfg, AgentManager agentManager) {
        String prop = cfg.getGroupByCapabilitiesProperty();
        String[] path = prop.split("\\.");

        // "persist" some of the metrics
        // if some "flavor" of agents disappear, we would want to show zero in the metric instead of no metric at all
        // we keep keys of all agent metrics in memory
        Set<Object> keys = new HashSet<>();
        Lock mutex = new ReentrantLock();

        collector = new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                Collection<AgentWorkerEntry> data = agentManager.getAvailableAgents();

                Map<Object, Long> m = new HashMap<>();

                Map<Object, Long> currentData = AgentWorkerUtils.groupBy(data, path);
                mutex.lock();
                try {
                    keys.addAll(currentData.keySet());
                    keys.forEach(k -> m.put(k, 0L));
                    m.putAll(currentData);
                } finally {
                    mutex.unlock();
                }

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
        try {
            CollectorRegistry.defaultRegistry.unregister(collector);
        } catch (Exception e) {
            log.warn("stop -> error while unregistering the collector: {}", e.getMessage());
        }
    }
}
