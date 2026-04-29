package com.walmartlabs.concord.plugins.mock;

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

import com.walmartlabs.concord.sdk.MapUtils;
import com.walmartlabs.concord.sdk.UserDefinedException;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public class MockDefinition {

    private final Map<String, Object> definition;

    public MockDefinition(Map<String, Object> definition) {
        this.definition = definition;
    }

    public String stepName() {
        return MapUtils.getString(definition, "stepName");
    }

    public Map<String, Object> stepMeta() {
        return MapUtils.getMap(definition, "stepMeta", Map.of());
    }

    public String task() {
        try {
            return MapUtils.assertString(definition, "task");
        } catch (IllegalArgumentException e) {
            throw new UserDefinedException("Invalid mock definition: " + e.getMessage() + "\n" + definition);
        }
    }

    public Map<String, Object> input() {
        return MapUtils.getMap(definition, "in", Map.of());
    }

    public Map<String, Object> out() {
        return MapUtils.getMap(definition, "out", Map.of());
    }

    public String method() {
        return MapUtils.getString(definition, "method");
    }

    public List<Object> args() {
        return MapUtils.getList(definition, "args", List.of());
    }

    public Serializable result() {
        return MapUtils.get(definition, "result", Serializable.class);
    }

    public String throwError() {
        return MapUtils.getString(definition, "throwError");
    }

    public String executeFlow() {
        return MapUtils.getString(definition, "executeFlow");
    }

    @Override
    public String toString() {
        return String.valueOf(definition);
    }
}
