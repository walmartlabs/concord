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

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CalendarFormatTest {

    /**
     * Expected values are produced by {@code javax.xml.bind.DatatypeConverter#printDateTime}.
     */
    @Test
    public void test() {
        assertEquals("2026-10-27T16:37:00Z", format("UTC", 0));
        assertEquals("2026-10-27T16:37:00.005Z", format("UTC", 5));
        assertEquals("2026-10-27T16:37:00.120Z", format("UTC", 120));
        assertEquals("2026-10-27T16:37:00.123Z", format("UTC", 123));
        assertEquals("2026-10-27T12:37:00-04:00", format("America/New_York", 0));
        assertEquals("2026-10-27T12:37:00.120-04:00", format("America/New_York", 120));
        assertEquals("2026-10-27T22:07:00.120+05:30", format("Asia/Kolkata", 120));
    }

    private static String format(String tz, int millis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(2026, Calendar.OCTOBER, 27, 16, 37, 0);
        c.set(Calendar.MILLISECOND, millis);

        Calendar local = Calendar.getInstance(TimeZone.getTimeZone(tz));
        local.setTimeInMillis(c.getTimeInMillis());
        return CalendarFormat.format(local);
    }
}
