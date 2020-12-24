package com.walmartlabs.concord.runtime.common.injector;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class TaskHolder<T> {

    private static final Logger log = LoggerFactory.getLogger(TaskHolder.class);

    private final Map<String, Class<T>> classes = new HashMap<>();

    public void add(String key, Class<T> value) {
        log.debug("Registering {} as '{}'...", value, key);
        Class<T> old = classes.put(key, value);
        if (old != null) {
            throw new IllegalStateException("Non-unique task name: " + key + ". " +
                    "Another task with the same name: " + old.getName());
        }
    }

    public Class<T> get(String key) {
        return classes.get(key);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(classes.keySet());
    }
}
