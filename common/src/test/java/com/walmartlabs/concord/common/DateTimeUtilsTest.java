package com.walmartlabs.concord.common;

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

import javax.xml.bind.DatatypeConverter;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoField;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateTimeUtilsTest {

    @Test
    public void test() throws Exception {
        Calendar now1 = Calendar.getInstance();
        now1.set(Calendar.MILLISECOND, 123);

        String a = DatatypeConverter.printDateTime(now1);

        OffsetDateTime now2 = OffsetDateTime.ofInstant(now1.toInstant(), ZoneId.of(now1.getTimeZone().getID()));
        String b = DateTimeUtils.toIsoString(now2);

        assertEquals(a, b);

        String src = "2020-07-16T13:47:51.085-04:00";
        OffsetDateTime x = DateTimeUtils.fromIsoString(src);
        String dst = DateTimeUtils.toIsoString(x);
        assertEquals(src, dst);

        src = "2020-07-16T17:13:27.912Z";
        x = DateTimeUtils.fromIsoString(src);

        assertEquals(x.toZonedDateTime().getZone().normalized(), ZoneId.of("UTC").normalized());
        assertEquals(x.get(ChronoField.YEAR), 2020);
        assertEquals(x.get(ChronoField.MILLI_OF_SECOND), 912);
    }
}
