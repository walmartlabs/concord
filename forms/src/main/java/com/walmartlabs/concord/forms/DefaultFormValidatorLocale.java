package com.walmartlabs.concord.forms;

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

public class DefaultFormValidatorLocale implements FormValidatorLocale {

    @Override
    public String noFieldsDefined(String formId) {
        return String.format("Form (%s) has no fields", formId);
    }

    @Override
    public String invalidCardinality(String formId, FormField field, Object value) {
        return String.format("%s: Invalid cardinality, expected %s", fieldName(field, null), spell(field.cardinality()));
    }

    @Override
    public String expectedString(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected a string value, got %s", fieldName(field, idx), value);
    }

    @Override
    public String expectedInteger(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected an integer value, got %s", fieldName(field, idx), value);
    }

    @Override
    public String expectedDecimal(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected a decimal value, got %s", fieldName(field, idx), value);
    }

    @Override
    public String expectedBoolean(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected a boolean value, got %s", fieldName(field, idx), value);
    }

    @Override
    public String doesntMatchPattern(String formId, FormField field, Integer idx, String pattern, Object value) {
        return String.format("%s: value '%s' doesn't match pattern '%s'", fieldName(field, idx), value, pattern);
    }

    @Override
    public String integerRangeError(String formId, FormField field, Integer idx, Long min, Long max, Object value) {
        return String.format("%s: value '%s' must be %s", fieldName(field, idx), value, bounds(min, max));
    }

    @Override
    public String decimalRangeError(String formId, FormField field, Integer idx, Double min, Double max, Object value) {
        return String.format("%s: value '%s' must be %s", fieldName(field, idx), value, bounds(min, max));
    }

    @Override
    public String valueNotAllowed(String formId, FormField field, Integer idx, Object allowed, Object value) {
        return String.format("%s: value '%s' is not allowed, valid values: %s", fieldName(field, idx), value, allowed);
    }

    @Override
    public String expectedDate(String formId, FormField field, Integer idx, Object value) {
        return String.format("%s: expected a date value, got %s", fieldName(field, idx), value);
    }

    public static String fieldName(FormField field, Integer idx) {
        String s = field.label();
        if (s == null) {
            s = field.name();
        }

        if (idx != null) {
            s = s + " [" + idx + "]";
        }

        return s;
    }

    public static String spell(FormField.Cardinality c) {
        if (c == null) {
            throw new IllegalArgumentException("Cardinality can't be null");
        }

        switch (c) {
            case ANY:
                return "any number of values";
            case ONE_AND_ONLY_ONE:
                return "a single value";
            case ONE_OR_NONE:
                return "a single optional value";
            case AT_LEAST_ONE:
                return "at least a single value";
            default:
                throw new IllegalArgumentException("Unsupported cardinality type: " + c);
        }
    }

    public static String bounds(Object min, Object max) {
        if (min != null && max != null) {
            return String.format("within %s and %s (inclusive)", min, max);
        } else if (min == null) {
            return String.format("less or equal than %s", max);
        } else {
            return String.format("equal or greater than %s", min);
        }
    }
}
