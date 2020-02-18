package com.walmartlabs.concord.runtime.v2.parser;

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

import com.fasterxml.jackson.core.JsonLocation;

import java.io.Serializable;
import java.util.*;

public class YamlObject extends YamlValue {

    private static final long serialVersionUID = 1L;

    protected final Map<String, YamlValue> values;

    public YamlObject(Map<String, YamlValue> values, JsonLocation location) {
        super(null, YamlValueType.OBJECT, location);
        this.values = values;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Serializable> getValue() {
        Map<String, Serializable> result = new HashMap<>();
        for (Map.Entry<String, YamlValue> e : values.entrySet()) {
            result.put(e.getKey(), e.getValue().getValue());
        }
        return result;
    }

    public Serializable remove(String name) {
        YamlValue v = values.remove(name);
        if (v == null) {
            return null;
        }
        return v.getValue();
    }

    public YamlValue getYamlValue(String name) {
        return values.get(name);
    }

    public <T> T getValue(String name, YamlValueType<T> type) {
        YamlValue v = values.get(name);
        if (v == null) {
            return null;
        }
        return v.getValue(type);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<String, YamlValue> getValues() {
        return values;
    }

    @Override
    public String toString() {
        return "YamlObject{" +
                "values=" + values +
                '}';
    }
}
