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

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.metrics.GaugeProvider;
import com.walmartlabs.concord.server.process.ProcessStatus;

import javax.inject.Named;

@Named
public class ProcessQueueGaugeModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<ProcessQueueDao> queueDaoProvider = getProvider(ProcessQueueDao.class);

        // TODO use a single query to fetch all statuses + derivative gauges?
        Multibinder<GaugeProvider> tasks = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        for (ProcessStatus s : ProcessStatus.values()) {
            tasks.addBinding().toInstance(create(queueDaoProvider, s));
        }
    }

    private static GaugeProvider<Integer> create(Provider<ProcessQueueDao> queueDaoProvider, ProcessStatus status) {
        return new GaugeProvider<Integer>() {
            @Override
            public String name() {
                return "process-queue-" + status.toString().toLowerCase();
            }

            @Override
            public Gauge<Integer> gauge() {
                return () -> queueDaoProvider.get().count(status);
            }
        };
    }
}
