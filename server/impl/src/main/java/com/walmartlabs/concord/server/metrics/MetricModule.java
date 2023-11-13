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
import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;
import com.walmartlabs.concord.server.sdk.metrics.GaugeProvider;
import com.walmartlabs.concord.server.sdk.metrics.WithTimer;

import static com.google.inject.Scopes.SINGLETON;
import static com.google.inject.multibindings.Multibinder.newSetBinder;
import static com.walmartlabs.concord.server.Utils.bindServletHolder;
import static com.walmartlabs.concord.server.Utils.bindSingletonBackgroundTask;
import static com.walmartlabs.concord.server.metrics.NoSyntheticMethodMatcher.INSTANCE;

public class MetricModule implements Module {

    @Override
    public void configure(Binder binder) {
        // common

        newSetBinder(binder, GaugeProvider.class);

        // registry

        binder.bind(MetricRegistry.class).toProvider(MetricRegistryProvider.class).in(SINGLETON);

        // @WithTimer stuff

        MetricInterceptor i = new MetricInterceptor();
        binder.requestInjection(i);

        binder.bindInterceptor(Matchers.any(), INSTANCE.and(Matchers.annotatedWith(WithTimer.class)), i);

        binder.bindListener(Matchers.any(), new MetricTypeListener());

        // tasks

        bindSingletonBackgroundTask(binder, FailedTaskMetrics.class);
        bindSingletonBackgroundTask(binder, WorkerMetrics.class);
        bindSingletonBackgroundTask(binder, MetricsRegistrator.class);

        // the /metrics endpoint

        bindServletHolder(binder, MetricsServletHolder.class);

        // Jetty stuff

        binder.install(new JettyStatisticsModule());
        binder.install(new JettySessionMetricsModule());
    }
}
