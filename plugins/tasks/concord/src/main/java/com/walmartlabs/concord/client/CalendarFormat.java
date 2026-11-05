package com.walmartlabs.concord.client;

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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

/**
 * Formats {@link Calendar} values the same way as the former
 * {@code javax.xml.bind.DatatypeConverter#printDateTime}: milliseconds are
 * omitted when zero, otherwise always printed with three digits.
 */
final class CalendarFormat {

    private static final DateTimeFormatter WITHOUT_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter WITH_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    static String format(Calendar c) {
        // Calendar can use custom TimeZone IDs or rules that ZoneId cannot represent.
        int offsetMillis = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET);
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(offsetMillis / 1000);
        OffsetDateTime t = OffsetDateTime.ofInstant(c.toInstant(), offset);
        return (c.get(Calendar.MILLISECOND) == 0 ? WITHOUT_MILLIS : WITH_MILLIS).format(t);
    }

    private CalendarFormat() {
    }
}
