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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Map;

@Named
@Singleton
public class ConcordObjectMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final ObjectMapper delegate;

    @Inject
    public ConcordObjectMapper(ObjectMapper objectMapper) {
        this.delegate = objectMapper;
    }

    public String serialize(Object m) {
        if (m == null) {
            return null;
        }

        try {
            return delegate.writeValueAsString(m);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Object> deserialize(Object s) {
        return deserialize(s, MAP_TYPE);
    }

    public <T> T deserialize(Object o, TypeReference valueTypeRef) {
        if (o == null) {
            return null;
        }

        try {
            return delegate.readValue(String.valueOf(o), valueTypeRef);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T deserialize(Object o, Class<T> valueType) {
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
}
