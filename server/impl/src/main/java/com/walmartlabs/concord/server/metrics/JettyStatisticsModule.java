package com.walmartlabs.concord.server.metrics;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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
import com.walmartlabs.concord.server.boot.HttpServer;
import com.walmartlabs.concord.server.sdk.metrics.GaugeProvider;

import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import org.eclipse.jetty.server.handler.StatisticsHandler;

public class JettyStatisticsModule extends AbstractModule {

    @Override
    protected void configure() {
        Provider<HttpServer> provider = getProvider(HttpServer.class);

        Multibinder<GaugeProvider> tasks = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        tasks.addBinding().toInstance(longAttribute("responses1xx", provider, StatisticsHandler::getResponses1xx));
        tasks.addBinding().toInstance(longAttribute("responses2xx", provider, StatisticsHandler::getResponses2xx));
        tasks.addBinding().toInstance(longAttribute("responses3xx", provider, StatisticsHandler::getResponses3xx));
        tasks.addBinding().toInstance(longAttribute("responses4xx", provider, StatisticsHandler::getResponses4xx));
        tasks.addBinding().toInstance(longAttribute("responses5xx", provider, StatisticsHandler::getResponses5xx));

        tasks.addBinding().toInstance(longAttribute("requestsActive", provider, StatisticsHandler::getRequestsActive));
        tasks.addBinding().toInstance(longAttribute("requestTimeMax", provider, StatisticsHandler::getRequestTimeMax));
        tasks.addBinding().toInstance(doubleAttribute("requestTimeMean", provider, StatisticsHandler::getRequestTimeMean));
    }

    private static GaugeProvider<Long> longAttribute(String attribute,
                                                     Provider<HttpServer> provider,
                                                     ToLongFunction<StatisticsHandler> value) {
        return new GaugeProvider<>() {
            @Override
            public String name() {
                return "jetty-" + attribute;
            }

            @Override
            public Gauge<Long> gauge() {
                StatisticsHandler statistics = provider.get().getStatisticsHandler();
                return () -> value.applyAsLong(statistics);
            }
        };
    }

    private static GaugeProvider<Double> doubleAttribute(String attribute,
                                                         Provider<HttpServer> provider,
                                                         ToDoubleFunction<StatisticsHandler> value) {
        return new GaugeProvider<>() {
            @Override
            public String name() {
                return "jetty-" + attribute;
            }

            @Override
            public Gauge<Double> gauge() {
                StatisticsHandler statistics = provider.get().getStatisticsHandler();
                return () -> value.applyAsDouble(statistics);
            }
        };
    }
}
