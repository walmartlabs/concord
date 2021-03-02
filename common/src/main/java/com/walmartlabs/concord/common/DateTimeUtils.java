package com.walmartlabs.concord.common;

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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeUtils {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Formats the supplied {@link OffsetDateTime} to an ISO-8601 string
     * ({@code yyyy-MM-ddTHH:mm:ss.SSSX})
     */
    public static String toIsoString(OffsetDateTime t) {
        return FORMAT.format(t);
    }

    /**
     * Parses the supplied {@code text} as an ISO-8601 date/time value
     * ({@code yyyy-MM-ddTHH:mm:ss.SSSX}).
     */
    public static OffsetDateTime fromIsoString(CharSequence text) {
        return OffsetDateTime.parse(text, FORMAT);
    }

    private DateTimeUtils() {
    }
}
