package com.walmartlabs.concord.runtime.common.injector;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.google.inject.TypeLiteral;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.walmartlabs.concord.common.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;

public final class InjectorUtils {

    private static final Logger log = LoggerFactory.getLogger(InjectorUtils.class);

    public static <T> TaskClassesListener<T> taskClassesListener(TaskHolder<T> holder) {
        return new TaskClassesListener<>(holder);
    }

    public static SubClassesOf subClassesOf(Class<?> baseClass) {
        return new SubClassesOf(baseClass);
    }

    private static class TaskClassesListener<T> implements TypeListener {

        private final TaskHolder<T> holder;

        private TaskClassesListener(TaskHolder<T> holder) {
            this.holder = holder;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <I> void hear(TypeLiteral<I> typeLiteral, TypeEncounter<I> typeEncounter) {
            Class<T> klass = (Class<T>) typeLiteral.getRawType();

            Named n = klass.getAnnotation(Named.class);
            if (n == null) {
                log.warn("Ignoring task class without @Named: {}", klass);
                return;
            }

            if (ReflectionUtils.findAnnotation(klass, Singleton.class) != null) {
                log.warn("Ignoring task class with @Singleton: {}", klass);
                return;
            }

            String key = n.value();
            if ("".equals(key)) {
                log.warn("Task class with an empty @Named value: {}", klass);
                return;
            }

            holder.add(key, klass);
        }
    }

    private static class SubClassesOf extends AbstractMatcher<TypeLiteral<?>> {

        private final Class<?> baseClass;

        private SubClassesOf(Class<?> baseClass) {
            this.baseClass = baseClass;
        }

        @Override
        public boolean matches(TypeLiteral<?> t) {
            return baseClass.isAssignableFrom(t.getRawType());
        }
    }

    private InjectorUtils() {
    }
}
