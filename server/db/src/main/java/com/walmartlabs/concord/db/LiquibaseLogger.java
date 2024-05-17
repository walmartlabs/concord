package com.walmartlabs.concord.db;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import liquibase.logging.core.AbstractLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.logging.Level;

public class LiquibaseLogger extends AbstractLogger {

    private static final Logger log = LoggerFactory.getLogger("liquibase");

    public LiquibaseLogger() {
        super(null);
    }

    @Override
    public void close() throws Exception {
        super.close();
    }

    @Override
    public void log(Level level, String message, Throwable e) {
        log.atLevel(org.slf4j.event.Level.INFO).log(message, e);
    }

    @Override
    public void severe(String message) {
        log.error(message);
    }

    @Override
    public void severe(String message, Throwable e) {
        log.error(message, e);
    }

    @Override
    public void warning(String message) {
        log.warn(message);
    }

    @Override
    public void warning(String message, Throwable e) {
        log.warn(message, e);
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void info(String message, Throwable e) {
        log.info(message, e);
    }

    @Override
    public void debug(String message) {
        log.debug(message);
    }

    @Override
    public void debug(String message, Throwable e) {
        log.debug(message, e);
    }
}
