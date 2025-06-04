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

import java.util.List;
import java.util.stream.Collectors;

public class YamlList extends YamlValue {

    private static final long serialVersionUID = 1L;

    private final List<YamlValue> values;

    public YamlList(List<YamlValue> values, Location location) {
        super(null, YamlValueType.ARRAY, location);
        this.values = values;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getValue() {
        return (T) values.stream()
                .map(YamlValue::getValue)
                .collect(Collectors.toList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getValue(YamlValueType<T> expectedType) throws InvalidValueTypeException {
        assertType(expectedType);
        return (T) values.stream()
                .map(YamlValue::getValue)
                .collect(Collectors.toList());
    }

    public <T> List<T> getListValue(YamlValueType<T> itemExpectedType) {
        return values.stream()
                .map(v -> v.getValue(itemExpectedType))
                .collect(Collectors.toList());
    }

    public <T> List<T> getListValue(ValueConverter<T> converter) {
        return values.stream()
                .map(converter::convert)
                .collect(Collectors.toList());
    }

    interface ValueConverter<T> {

        T convert(YamlValue value);
    }
}
