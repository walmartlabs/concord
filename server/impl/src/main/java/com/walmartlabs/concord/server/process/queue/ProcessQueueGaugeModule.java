package com.walmartlabs.concord.server.process.queue;

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

import com.codahale.metrics.CachedGauge;
import com.codahale.metrics.DerivativeGauge;
import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.metrics.GaugeProvider;
import com.walmartlabs.concord.server.sdk.ProcessStatus;

import javax.inject.Named;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Named
public class ProcessQueueGaugeModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<ProcessQueueDao> queueDaoProvider = getProvider(ProcessQueueDao.class);

        // create the base gauge that caches all individual values
        Gauge<Map<ProcessStatus, Integer>> base = new CachedGauge<Map<ProcessStatus, Integer>>(5, TimeUnit.SECONDS) {
            @Override
            protected Map<ProcessStatus, Integer> loadValue() {
                return queueDaoProvider.get().getStatistics();
            }
        };

        Multibinder<GaugeProvider> gauges = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        gauges.addBinding().toInstance(createBaseProvider(base));
        for (ProcessStatus s : ProcessStatus.values()) {
            gauges.addBinding().toInstance(create(base, s));
        }
    }

    private static GaugeProvider<Map<ProcessStatus, Integer>> createBaseProvider(Gauge<Map<ProcessStatus, Integer>> base) {
        return new GaugeProvider<Map<ProcessStatus, Integer>>() {
            @Override
            public String name() {
                return "process-queue-statistics";
            }

            @Override
            public Gauge<Map<ProcessStatus, Integer>> gauge() {
                return base;
            }
        };
    }

    private static GaugeProvider<Integer> create(Gauge<Map<ProcessStatus, Integer>> base, ProcessStatus status) {
        return new GaugeProvider<Integer>() {
            @Override
            public String name() {
                return "process-queue-" + status.toString().toLowerCase();
            }

            @Override
            public Gauge<Integer> gauge() {
                return new DerivativeGauge<Map<ProcessStatus, Integer>, Integer>(base) {
                    @Override
                    protected Integer transform(Map<ProcessStatus, Integer> value) {
                        return value.getOrDefault(status, 0);
                    }
                };
            }
        };
    }
}
