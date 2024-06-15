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
import com.google.inject.multibindings.Multibinder;
import com.walmartlabs.concord.server.sdk.metrics.GaugeProvider;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;

public class JettySessionMetricsModule extends AbstractModule {

    private static final String[] ATTRIBUTES = {
            "sessionsCurrent",
            "sessionsMax",
            "sessionsTotal"
    };

    @Override
    protected void configure() {
        Multibinder<GaugeProvider> tasks = Multibinder.newSetBinder(binder(), GaugeProvider.class);
        for (String a : ATTRIBUTES) {
            tasks.addBinding().toInstance(attribute(a));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> GaugeProvider<T> attribute(String attribute) {
        return new GaugeProvider<T>() {
            @Override
            public String name() {
                return "jetty-" + attribute;
            }

            @Override
            public Gauge<T> gauge() {
                return () -> (T) getAttribute(attribute);
            }
        };
    }

    private static Object getAttribute(String attribute) {
        try {
            MBeanServer mBeans = ManagementFactory.getPlatformMBeanServer();
            return mBeans.getAttribute(new ObjectName("org.eclipse.jetty.session:context=ROOT,id=0,type=defaultsessioncache"), attribute);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
