package com.walmartlabs.concord.plugins.misc;

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

import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;

import javax.inject.Named;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Named("datetime")
@DryRunReady
public class DateTimeTaskV2 implements Task {

    public Date current() {
        return new Date();
    }

    public String current(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(ZonedDateTime.now());
    }

    public String currentWithZone(String zone, String pattern) {
        return DateTimeFormatter.ofPattern(pattern).format(ZonedDateTime.now(ZoneId.of(zone)));
    }

    public String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    public Date parse(String src, String pattern) throws ParseException {
        return new SimpleDateFormat(pattern).parse(src);
    }
}
