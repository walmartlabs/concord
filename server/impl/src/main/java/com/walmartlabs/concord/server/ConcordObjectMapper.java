package com.walmartlabs.concord.server;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.JSONB;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

public class ConcordObjectMapper {

    public static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F]");

    private final ObjectMapper delegate;

    @Inject
    public ConcordObjectMapper(ObjectMapper objectMapper) {
        this.delegate = objectMapper;
    }

    public JSONB toJSONB(Object m) {
        if (m == null) {
            return null;
        }

        return JSONB.valueOf(removeUnsupportedEscape(toString(m, null)));
    }

    public <T> JSONB toJSONB(T m, TypeReference<T> type) {
        if (m == null) {
            return null;
        }

        return JSONB.valueOf(removeUnsupportedEscape(toString(m, type)));
    }

    public JSONB jsonStringToJSONB(String json) {
        if (json == null) {
            return null;
        }

        return JSONB.valueOf(removeUnsupportedEscape(json));
    }

    public String toString(Object m) {
        return toString(m, null);
    }

    public <T> String toString(T m, TypeReference<T> type) {
        if (m == null) {
            return null;
        }

        try {
            if (type == null) {
                return delegate.writeValueAsString(m);
            } else {
                return delegate.writerFor(type).writeValueAsString(m);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> fromJSONB(JSONB o) {
        return fromJSONB(o, MAP_TYPE);
    }

    public <T> T fromJSONB(JSONB o, TypeReference<T> valueTypeRef) {
        if (o == null) {
            return null;
        }

        return deserialize(o.toString(), valueTypeRef);
    }

    public <T> T fromJSONB(JSONB o, Class<T> valueType) {
        if (o == null) {
            return null;
        }

        return deserialize(o.toString(), valueType);
    }

    public <T> T fromString(String s, Class<T> valueType) {
        return deserialize(s, valueType);
    }

    private static String removeUnsupportedEscape(String str) {
        return CONTROL_CHARS.matcher(str).replaceAll("");
    }

    private <T> T deserialize(String o, TypeReference<T> valueTypeRef) {
        if (o == null) {
            return null;
        }

        try {
            return delegate.readValue(o, valueTypeRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T deserialize(Object o, Class<T> valueType) {
        if (o == null) {
            return null;
        }

        try {
            return delegate.readValue(String.valueOf(o), valueType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> convertToMap(Object o) {
        return delegate.convertValue(o, MAP_TYPE);
    }

    public <T> T convertValue(Object o, TypeReference<T> valueTypeRef) {
        return delegate.convertValue(o, valueTypeRef);
    }

    public <T> T convertValue(Object o, Class<T> toValueType) {
        return delegate.convertValue(o, toValueType);
    }
}
