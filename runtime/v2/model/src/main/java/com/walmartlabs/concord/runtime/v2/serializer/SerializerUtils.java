package com.walmartlabs.concord.runtime.v2.serializer;

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

import com.fasterxml.jackson.core.JsonGenerator;
import com.walmartlabs.concord.runtime.v2.model.Loop;
import com.walmartlabs.concord.runtime.v2.model.WithItems;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public final class SerializerUtils {

    public static void writeNotEmptyObjectField(String fieldName, String value, JsonGenerator gen) throws IOException {
        if (value == null || value.trim().isEmpty()) {
            return;
        }

        gen.writeObjectField(fieldName, value);
    }

    public static <K, V> void writeNotEmptyObjectField(String fieldName, Map<K, V> value, JsonGenerator gen) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }

        gen.writeObjectField(fieldName, value);
    }

    public static <T> void writeNotEmptyObjectField(String fieldName, Collection<T> value, JsonGenerator gen) throws IOException {
        if (value == null || value.isEmpty()) {
            return;
        }

        gen.writeObjectField(fieldName, value);
    }

    @Deprecated
    public static void writeWithItems(WithItems items, JsonGenerator gen) throws IOException {
        switch (items.mode()) {
            case PARALLEL: {
                gen.writeObjectField("parallelWithItems", items);
                break;
            }
            case SERIAL: {
                gen.writeObjectField("withItems", items);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported withItems mode: " + items.mode());
        }
    }

    public static void writeLoop(Loop loop, JsonGenerator gen) throws IOException {
        if (loop == null) {
            return;
        }

        gen.writeObjectField("loop", loop);
    }

    private SerializerUtils() {
    }
}
