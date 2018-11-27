package com.walmartlabs.concord.server.org.triggers;

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

import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoZonedDateTime;

public final class CronUtils {

    public static Instant nextExecution(Instant now, String expression, ZoneId zone) {
        if (zone == null) {
            zone = ZoneId.systemDefault();
        }
        return nextExecution(ZonedDateTime.ofInstant(now, zone), expression);
    }

    public static Instant nextExecution(String expression, ZoneId zone) {
        if (zone == null) {
            zone = ZoneId.systemDefault();
        }
        return nextExecution(ZonedDateTime.now(zone), expression);
    }

    private static Instant nextExecution(ZonedDateTime now, String expression) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX));
        ExecutionTime executionTime = ExecutionTime.forCron(parser.parse(expression));
        return executionTime.nextExecution(now).map(ChronoZonedDateTime::toInstant).orElse(null);
    }

    private CronUtils() {
    }
}
