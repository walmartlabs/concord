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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

import java.lang.reflect.Field;

public class MetricTypeListener implements TypeListener {

    @Override
    public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
        Class<?> clazz = type.getRawType();
        while (clazz != null) {
            for (Field f : clazz.getDeclaredFields()) {
                InjectMeter i = f.getAnnotation(InjectMeter.class);
                if (f.getType() == Meter.class && i != null) {
                    String name = i.value();
                    if (name.isEmpty()) {
                        name = f.getName();
                    }

                    String fqn = MetricUtils.createFqn("meter", clazz, name, null);

                    Provider<Injector> injector = encounter.getProvider(Injector.class);
                    encounter.register((MembersInjector<I>) instance -> {
                        MetricRegistry registry = injector.get().getInstance(MetricRegistry.class);
                        try {
                            boolean accessible = f.isAccessible();
                            f.setAccessible(true);
                            f.set(instance, registry.meter(fqn));
                            f.setAccessible(accessible);
                        } catch (IllegalAccessException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }

            clazz = clazz.getSuperclass();
        }
    }

}
