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

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.server.sdk.metrics.InjectCounter;
import com.walmartlabs.concord.server.sdk.metrics.InjectMeter;

import java.lang.reflect.Field;

public class MetricTypeListener implements TypeListener {

    @Override
    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        Class<?> clazz = type.getRawType();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                processMeters(encounter, clazz, f);
                processCounters(encounter, clazz, f);
            }

            clazz = clazz.getSuperclass();
        }
    }

    private static <I> void processMeters(TypeEncounter<I> encounter, Class<?> clazz, Field f) {
        InjectMeter i = f.getAnnotation(InjectMeter.class);
        if (f.getType() != Meter.class || i == null) {
            return;
        }

        String name = i.value();
        if (name.isEmpty()) {
            name = f.getName();
        }

        String fqn = MetricUtils.createFqn("meter", clazz, name, null);

        Provider<Injector> injector = encounter.getProvider(Injector.class);
        encounter.register((MembersInjector<I>) instance -> {
            MetricRegistry registry = injector.get().getInstance(MetricRegistry.class);
            set(f, instance, registry.meter(fqn));
        });
    }

    private static <I> void processCounters(TypeEncounter<I> encounter, Class<?> clazz, Field f) {
        InjectCounter i = f.getAnnotation(InjectCounter.class);
        if (f.getType() != Counter.class || i == null) {
            return;
        }

        String name = i.value();
        if (name.isEmpty()) {
            name = f.getName();
        }

        String fqn = MetricUtils.createFqn("counter", clazz, name, null);

        Provider<Injector> injector = encounter.getProvider(Injector.class);
        encounter.register((MembersInjector<I>) instance -> {
            MetricRegistry registry = injector.get().getInstance(MetricRegistry.class);
            set(f, instance, registry.counter(fqn));
        });
    }

    private static void set(Field f, Object i, Object v) {
        try {
            boolean accessible = f.isAccessible();
            f.setAccessible(true);
            f.set(i, v);
            f.setAccessible(accessible);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
