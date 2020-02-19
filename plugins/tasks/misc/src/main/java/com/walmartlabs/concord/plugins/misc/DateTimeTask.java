package com.walmartlabs.concord.plugins.misc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

@Named("datetime")
public class DateTimeTask implements Task {

    public Date current() {
        return new Date();
    }

    public String currentWithZone(String zone, String pattern) {
            return ZonedDateTime.now(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern(pattern));
    }

    public String currentWithZone(String zone) {
        return ZonedDateTime.now(ZoneId.of(zone)).toString();
    }

    public String current(String pattern) {
        return new SimpleDateFormat(pattern).format(new Date());
    }

    public String format(Date date, String pattern) {
        return new SimpleDateFormat(pattern).format(date);
    }

    public Date parse(String dateStr, String format) throws ParseException {
        return new SimpleDateFormat(format).parse(dateStr);
    }
}
