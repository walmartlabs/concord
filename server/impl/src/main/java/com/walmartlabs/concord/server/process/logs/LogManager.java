package com.walmartlabs.concord.server.process.logs;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Named
@Singleton
public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ProcessLogsDao logsDao;

    @Inject
    public LogManager(ProcessLogsDao logsDao) throws IOException {
        this.logsDao = logsDao;
    }

    public void debug(UUID instanceId, String log, Object... args) {
        log_(instanceId, LogLevel.DEBUG, log, args);
    }

    public void info(UUID instanceId, String log, Object... args) {
        log_(instanceId, LogLevel.INFO, log, args);
    }

    public void warn(UUID instanceId, String log, Object... args) {
        log_(instanceId, LogLevel.WARN, log, args);
    }

    public void error(UUID instanceId, String log, Object... args) {
        log_(instanceId, LogLevel.ERROR, log, args);
    }

    public void log(UUID instanceId, String msg) throws IOException {
        log(instanceId, msg.getBytes());
    }

    public void log(UUID instanceId, byte[] msg) throws IOException {
        logsDao.append(instanceId, msg);
    }

    private void log_(UUID instanceId, LogLevel level, String msg, Object... args) {
        try {
            log(instanceId, formatMessage(level, msg, args));
        } catch (IOException e) {
            log.error("log ['{}', {}] -> error", instanceId, level, e);
        }
    }

    private static String formatMessage(LogLevel level, String log, Object... args) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        FormattingTuple m = MessageFormatter.arrayFormat(log, args);
        if (m.getThrowable() != null) {
            return String.format("%s [%-5s] %s%n%s%n", timestamp, level.name(), m.getMessage(),
                    formatException(m.getThrowable()));
        } else {
            return String.format("%s [%-5s] %s%n", timestamp, level.name(), m.getMessage());
        }
    }

    private static String formatException(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private enum LogLevel {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
