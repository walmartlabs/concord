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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public final class SerializerUtils {

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

    private SerializerUtils() {
    }
}
