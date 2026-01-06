package com.walmartlabs.concord.server.process.queue;

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

import com.codahale.metrics.Gauge;
import com.google.inject.AbstractModule;
import com.google.inject.Provider;
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.sdk.metrics.GaugeProvider;
import com.walmartlabs.concord.server.sdk.ProcessKeyCache;


import java.util.function.ToLongFunction;

public class ProcessKeyCacheGaugeModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<ProcessKeyCache> provider = getProvider(ProcessKeyCache.class);

        Multibinder<GaugeProvider> gauges = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        gauges.addBinding().toInstance(create("hit-count", provider, ProcessKeyCache::hitCount));
        gauges.addBinding().toInstance(create("miss-count", provider, ProcessKeyCache::missCount));
    }

    private static GaugeProvider<Long> create(String suffix, Provider<ProcessKeyCache> provider, ToLongFunction<ProcessKeyCache> value) {
        return new GaugeProvider<>() {
            @Override
            public String name() {
                return "process-key-cache-" + suffix;
            }

            @Override
            public Gauge<Long> gauge() {
                ProcessKeyCache cache = provider.get();
                return () -> value.applyAsLong(cache);
            }
        };
    }
}
