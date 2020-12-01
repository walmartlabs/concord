package com.walmartlabs.concord.common;

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

import org.slf4j.MDC;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

public final class LogUtils {

    // the UI expects log timestamps in a specific format to be able to convert it to the local time
    // see also runner/src/main/resources/logback.xml and console2/src/components/molecules/ProcessLogViewer/datetime.tsx
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);
    private static final ZoneId DEFAULT_TIMESTAMP_TZ = ZoneId.of("UTC");

    public enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public static String formatMessage(LogLevel level, String log, Object... args) {
        String timestamp = ZonedDateTime.now(DEFAULT_TIMESTAMP_TZ).format(TIMESTAMP_FORMAT);
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        if (m.getThrowable() != null) {
            return String.format("%s [%-5s] %s%n%s%n", timestamp, level.name(), m.getMessage(), formatException(m.getThrowable()));
        }

        return String.format("%s [%-5s] %s%n", timestamp, level.name(), m.getMessage());
    }

    public static Runnable withMdc(Runnable runnable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            MDC.setContextMap(mdc);
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public static <T> Callable<T> withMdc(Callable<T> callable) {
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        return () -> {
            MDC.setContextMap(mdc);
            try {
                return callable.call();
            } finally {
                MDC.clear();
            }
        };
    }

    private static String formatException(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private LogUtils() {
    }
}
