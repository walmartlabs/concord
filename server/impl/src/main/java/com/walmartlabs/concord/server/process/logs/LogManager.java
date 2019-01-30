package com.walmartlabs.concord.server.process.logs;

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

import com.walmartlabs.concord.common.LogUtils;
import com.walmartlabs.concord.server.process.ProcessKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static com.walmartlabs.concord.common.LogUtils.LogLevel;

@Named
@Singleton
public class LogManager {

    private static final Logger log = LoggerFactory.getLogger(LogManager.class);

    private final ProcessLogsDao logsDao;

    @Inject
    public LogManager(ProcessLogsDao logsDao) {
        this.logsDao = logsDao;
    }

    public void info(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.INFO, log, args);
    }

    public void warn(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.WARN, log, args);
    }

    public void error(ProcessKey processKey, String log, Object... args) {
        log(processKey, LogLevel.ERROR, log, args);
    }

    public void log(ProcessKey processKey, String msg) {
        log(processKey, msg.getBytes());
    }

    public void log(ProcessKey processKey, byte[] msg) {
        logsDao.append(processKey, msg);
    }

    private void log(ProcessKey processKey, LogLevel level, String msg, Object... args) {
        log(processKey, LogUtils.formatMessage(level, msg, args));
    }
}
