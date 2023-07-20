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

import com.walmartlabs.concord.server.sdk.BackgroundTask;
import com.walmartlabs.concord.server.task.SchedulerDao;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.GaugeMetricFamily;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FailedTaskMetrics implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(FailedTaskMetrics.class);

    private final Collector collector;

    @Inject
    public FailedTaskMetrics(SchedulerDao schedulerDao){
        collector = new Collector() {
            @Override
            public List<MetricFamilySamples> collect() {
                Collection<FailedTaskError> data = schedulerDao.pollErrored();
                GaugeMetricFamily f = new GaugeMetricFamily("failed_tasks", "Errors for failed server tasks", Arrays.asList("task_id", "task_error", "task_error_at"));
                data.forEach( k ->
                    f.addMetric(Arrays.asList(k.getTaskId(), k.getTaskError(), k.getTaskErrorAt().toString()), 1L)
                );
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
