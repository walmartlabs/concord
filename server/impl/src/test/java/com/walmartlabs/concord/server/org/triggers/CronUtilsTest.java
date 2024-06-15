package com.walmartlabs.concord.server.org.triggers;

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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CronUtilsTest {

    @Test
    public void test() {
        OffsetDateTime now = OffsetDateTime.parse("2020-07-17T10:00:01-04:00");

        // ---

        String spec = "0 10 * * *";
        ZoneId zoneId = ZoneId.of("America/Toronto");

        OffsetDateTime next = CronUtils.nextExecution(now, spec, zoneId);
        // next morning
        assertEquals("2020-07-18T10:00:00-04:00", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(next));

        // ---

        spec = "* * * * *";
        zoneId = ZoneId.of("UTC");

        // next minute
        next = CronUtils.nextExecution(now, spec, zoneId);
        assertEquals("2020-07-17T14:01:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(next));

        // next after next
        next = CronUtils.nextExecution(next, spec, zoneId);
        assertEquals("2020-07-17T14:02:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(next));

        Duration interval = Duration.between(next, CronUtils.nextExecution(next, spec, zoneId));
        assertEquals(TimeUnit.MINUTES.toMillis(1), interval.toMillis());
    }
}
