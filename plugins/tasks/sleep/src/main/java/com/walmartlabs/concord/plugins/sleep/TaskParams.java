package com.walmartlabs.concord.plugins.sleep;

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

import com.walmartlabs.concord.runtime.v2.sdk.MapBackedVariables;
import com.walmartlabs.concord.runtime.v2.sdk.Variables;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Map;

public class TaskParams {

    private final Variables input;

    public TaskParams(Map<String, Object> input) {
        this(new MapBackedVariables(input));
    }

    public TaskParams(Variables input) {
        this.input = input;
    }

    public Number duration() {
        return input.getNumber(Constants.DURATION_KEY, null);
    }

    public Instant until() {
        Object value = input.get(Constants.UNTIL_KEY);
        if (value == null) {
            return null;
        }

        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }

        if (value instanceof String) {
            try {
                return ZonedDateTime.parse((String) value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid datetime string. Expected " + Constants.DATETIME_PATTERN +
                        " (e.g. " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()) + ") got: " + value);
            }
        }

        throw new IllegalArgumentException("Invalid variable '" + value + "' type, expected: date/string, got: " + value.getClass());
    }

    public boolean suspend() {
        return input.getBoolean(Constants.SUSPEND_KEY, false);
    }
}
