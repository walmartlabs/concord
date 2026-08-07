package com.walmartlabs.concord.runtime.v25.model;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Configuration;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record Configuration25(Map<String, Object> values, SourceRange sourceRange) implements Configuration, Serializable {

    public Configuration25(Map<String, Object> values) {
        this(values, null);
    }

    public Configuration25 {
        values = Values.map(values);
    }

    @Override
    public Map<String, Object> asMap() {
        return values;
    }

    @Override
    public List<String> dependencies() {
        return stringList("dependencies");
    }

    @Override
    public List<String> extraDependencies() {
        return stringList("extraDependencies");
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> arguments() {
        var result = values.get("arguments");
        return result instanceof Map<?, ?> ? (Map<String, Object>) result : Map.of();
    }

    public String entryPoint() {
        var result = values.get("entryPoint");
        return result != null ? result.toString() : "default";
    }

    public List<String> activeProfiles() {
        return stringList("activeProfiles");
    }

    public int parallelLoopParallelism() {
        var result = values.get("parallelLoopParallelism");
        return result instanceof Number number ? number.intValue() : Runtime.getRuntime().availableProcessors();
    }

    private List<String> stringList(String key) {
        var result = values.get(key);
        if (!(result instanceof List<?> valuesList)) {
            return List.of();
        }
        return valuesList.stream().map(Object::toString).toList();
    }
}
