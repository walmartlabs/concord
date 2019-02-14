package com.walmartlabs.concord.sdk;

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

import java.util.Map;
import java.util.UUID;

public final class MapUtils {

    public static UUID getUUID(Map<String, Object> m, String name) {
        Object o = m.get(name);
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return UUID.fromString((String)o);
        }
        if (o instanceof UUID) {
            return (UUID) o;
        }
        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: string/uuid, got: " + o.getClass());
    }

    public static String getString(Map<String, Object> m, String name) {
        return getString(m, name, null);
    }

    public static String getString(Map<String, Object> m, String name, String defaultValue) {
        return get(m, name, defaultValue, String.class);
    }

    public static <T> T get(Map<String, Object> m, String name, T defaultValue, Class<T> type) {
        Object value = get(m, name, defaultValue);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: " + type + ", got: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Map<String, Object> m, String name, T defaultValue) {
        return (T) m.getOrDefault(name, defaultValue);
    }

    private MapUtils() {
    }
}
