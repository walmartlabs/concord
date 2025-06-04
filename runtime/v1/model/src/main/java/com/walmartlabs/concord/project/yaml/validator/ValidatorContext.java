package com.walmartlabs.concord.project.yaml.validator;

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

import com.fasterxml.jackson.core.JsonLocation;

import java.util.HashMap;
import java.util.Map;

public class ValidatorContext {
    private final Map<String, Map<String, JsonLocation>> counters = new HashMap<>();

    public void assertUnique(String counterName, String key, JsonLocation location) {
        Map<String, JsonLocation> keys = counters.computeIfAbsent(counterName, k -> new HashMap<>());
        JsonLocation old = keys.put(key, location);
        if (old != null) {
            throw new IllegalArgumentException(counterName + " '" + key + "' @:" + location + " already defined at @:" + old);
        }
    }
}
