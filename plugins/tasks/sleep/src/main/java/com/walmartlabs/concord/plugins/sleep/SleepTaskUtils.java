package com.walmartlabs.concord.plugins.sleep;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class SleepTaskUtils {

    private static final Logger log = LoggerFactory.getLogger(SleepTaskUtils.class);

    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.
            ofPattern(Constants.DATETIME_PATTERN).withZone(ZoneOffset.UTC);

    public static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static long toSleepDuration(Number duration, Instant until) {
        if (duration != null) {
            return duration.longValue() * 1000;
        }

        return Duration.between(Instant.now(), until).toMillis();
    }

    public static Instant toSleepUntil(Number duration, Instant until) {
        if (until != null) {
            return until;
        }

        return Instant.now().plusSeconds(duration.longValue());
    }

    public static Instant getUntil(Object value) {
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }

        if (value instanceof String) {
            try {
                return ZonedDateTime.parse((CharSequence) value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid datetime string. Expected " + Constants.DATETIME_PATTERN +
                        " (e.g. " + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()) + ") got: " + value);
            }
        }

        throw new IllegalArgumentException("Invalid variable '" + value + "' type, expected: date/string, got: " + value.getClass());
    }

    public static Map<String, Object> createCondition(Instant until) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_SLEEP");
        condition.put("until", dateFormatter.format(until));
        condition.put("reason", "Waiting till " + until);
        condition.put("resumeEvent", Constants.RESUME_EVENT_NAME);
        return condition;
    }

    public static void validateInputParams(Number duration, Instant until) {
        if (duration == null && until == null) {
            log.error("Invalid sleep task input parameters: 'duration' or 'until' must be specified");
            throw new IllegalArgumentException("Invalid arguments");
        }

        if (duration != null && until != null) {
            log.error("Invalid sleep task input parameters: 'duration' and 'until' are mutually exclusive");
            throw new IllegalArgumentException("Invalid arguments");
        }
    }
}
