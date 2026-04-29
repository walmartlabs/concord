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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.runtime.v2.exception.InvalidValueTypeException;

import java.io.Serializable;

public class YamlValue implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Object value;

    private final YamlValueType<?> type;

    private final Location location;

    public <T> YamlValue(T value, YamlValueType<T> type, Location location) {
        this.value = value;
        this.type = type;
        this.location = location;
    }

    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) value;
    }

    public <T> T getValue(YamlValueType<T> expectedType) throws InvalidValueTypeException {
        assertType(expectedType);
        return getValue();
    }

    public YamlValueType<?> getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    protected <T> void assertType(YamlValueType<T> expectedType) throws InvalidValueTypeException {
        if (expectedType != type) {
            throw InvalidValueTypeException.builder()
                    .expected(expectedType)
                    .actual(type)
                    .location(location)
                    .build();
        }
    }

    @Override
    public String toString() {
        return "YamlValue{" +
                "value=" + value +
                ", type=" + type +
                ", location=" + location +
                '}';
    }
}
