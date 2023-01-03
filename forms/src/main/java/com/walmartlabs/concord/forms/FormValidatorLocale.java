package com.walmartlabs.concord.forms;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

public interface FormValidatorLocale {

    /**
     * The form has no fields defined.
     */
    String noFieldsDefined(String formId);

    /**
     * Invalid cardinality of a value.
     */
    String invalidCardinality(String formId, FormField field, Object value);

    /**
     * Expected a string value.
     */
    String expectedString(String formId, FormField field, Integer idx, Object value);

    /**
     * Expected an integer value.
     */
    String expectedInteger(String formId, FormField field, Integer idx, Object value);

    /**
     * Expected a decimal value.
     */
    String expectedDecimal(String formId, FormField field, Integer idx, Object value);

    /**
     * Expected a boolean value.
     */
    String expectedBoolean(String formId, FormField field, Integer idx, Object value);

    /**
     * A string value doesn't match the specified pattern.
     */
    String doesntMatchPattern(String formId, FormField field, Integer idx, String pattern, Object value);

    /**
     * Value must be within the specified range.
     */
    String integerRangeError(String formId, FormField field, Integer idx, Long min, Long max, Object value);

    /**
     * Value must be within the specified range.
     */
    String decimalRangeError(String formId, FormField field, Integer idx, Double min, Double max, Object value);

    /**
     * Value is not allowed.
     */
    String valueNotAllowed(String formId, FormField field, Integer idx, Object allowed, Object value);

    /**
     * Expected a date value.
     *
     */
    String expectedDate(String formId, FormField field, Integer idx, Object value);
}
