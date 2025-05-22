package com.walmartlabs.concord.runtime.v2.parser;

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

import com.walmartlabs.concord.runtime.model.Location;

import java.util.*;

public final class YamlObjectConverter {

    public static YamlObject from(Map<String, Object> value, Location location) {
        Map<String, YamlValue> values = new LinkedHashMap<>();

        for (Map.Entry<String, Object> e : value.entrySet()) {
            YamlValue v = fromObject(e.getValue(), location);
            if (v != null) {
                values.put(e.getKey(), v);
            }
        }

        return new YamlObject(values, location);
    }

    @SuppressWarnings("unchecked")
    private static YamlValue fromObject(Object value, Location location) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return new YamlValue((String)value, YamlValueType.STRING, location);
        } else if (value instanceof Integer) {
            return new YamlValue((Integer) value, YamlValueType.INT, location);
        } else if (value instanceof Float) {
            return new YamlValue((Float)value, YamlValueType.FLOAT, location);
        } else if (value instanceof Number) {
            return new YamlValue(((Number) value).intValue(), YamlValueType.INT, location);
        } else if (value instanceof Boolean) {
            return new YamlValue((Boolean)value, YamlValueType.BOOLEAN, location);
        } else if (value instanceof Collection) {
            return fromCollection((Collection<Object>)value, location);
        } else if (value instanceof Map) {
            return from((Map<String, Object>)value, location);
        } else if (value.getClass().isArray()) {
            return fromCollection(new ArrayList<>(Arrays.asList((Object[]) value)), location);
        }

        return null;
    }

    private static YamlList fromCollection(Collection<Object> rawValues, Location location) {
        List<YamlValue> values = new ArrayList<>();
        for (Object v : rawValues) {
            YamlValue value = fromObject(v, location);
            if (value != null) {
                values.add(value);
            }
        }
        return new YamlList(values, location);
    }

    private YamlObjectConverter() {
    }
}
