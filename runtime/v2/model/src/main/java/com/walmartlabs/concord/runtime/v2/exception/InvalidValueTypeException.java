package com.walmartlabs.concord.runtime.v2.exception;

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
import com.walmartlabs.concord.runtime.v2.parser.YamlValueType;

import java.util.Arrays;
import java.util.StringJoiner;

public class InvalidValueTypeException extends YamlProcessingException {

    private final YamlValueType[] expectedType;
    private final YamlValueType actualType;
    private final String valueKey;

    private InvalidValueTypeException(String valueKey, YamlValueType[] expectedType, YamlValueType actualType, JsonLocation location) {
        super(location);
        this.expectedType = expectedType;
        this.actualType = actualType;
        this.valueKey = valueKey;
    }

    public YamlValueType[] getExpectedType() {
        return expectedType;
    }

    public YamlValueType getActualType() {
        return actualType;
    }

    @Override
    public String getMessage() {
        return buildMessage();
    }

    private String buildMessage() {
        String msg = buildPrefix() + ", expected: " + typeToString(expectedType) + ", got: " + actualType;
        if (actualType == YamlValueType.NULL) {
            msg += ". Remove attribute or complete the definition";
        }
        return msg;
    }

    private String buildPrefix() {
        if (valueKey != null) {
            return "Invalid value type of '" + valueKey + "' parameter";
        } else {
            return "Invalid value type";
        }
    }

    private static String typeToString(YamlValueType[] expectedTypes) {
        if (expectedTypes.length == 1) {
            return expectedTypes[0].toString();
        }

        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        Arrays.stream(expectedTypes).forEach(e -> joiner.add(e.toString()));
        return joiner.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String valueKey;
        private YamlValueType[] expectedType;
        private YamlValueType actualType;
        private JsonLocation location;

        public Builder from(InvalidValueTypeException e) {
            this.expectedType = e.getExpectedType();
            this.actualType = e.getActualType();
            this.location = e.getLocation();
            this.valueKey = e.valueKey;
            return this;
        }

        public Builder valueKey(String key) {
            this.valueKey = key;
            return this;
        }

        public Builder expected(YamlValueType... expected) {
            this.expectedType = expected;
            return this;
        }

        public Builder actual(YamlValueType actual) {
            this.actualType = actual;
            return this;
        }

        public Builder location(JsonLocation location) {
            this.location = location;
            return this;
        }

        public InvalidValueTypeException build() {
            return new InvalidValueTypeException(valueKey, expectedType, actualType, location);
        }
    }
}
