package com.walmartlabs.concord.runtime.v2.runner.tasks;

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

import com.google.inject.BindingAnnotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Retention;
import java.util.HashMap;
import java.util.Map;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class TaskHolder<T> {

    private static final Logger log = LoggerFactory.getLogger(TaskHolder.class);

    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface V1 {
    }

    @Retention(RUNTIME)
    @BindingAnnotation
    public @interface V2 {
    }

    private final Map<String, Class<T>> classes = new HashMap<>();

    public void add(String key, Class<T> value) {
        log.debug("Registering {} as '{}'...", value, key);
        Class<T> old = classes.put(key, value);
        if (old != null) {
            throw new IllegalStateException("Non-unique task name: " + key);
        }
    }

    public Class<T> get(String key) {
        return classes.get(key);
    }
}
