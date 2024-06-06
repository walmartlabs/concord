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

import static java.util.logging.Level.CONFIG;

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
        var l = level.intValue();
        if (l < CONFIG.intValue()) {
            log.debug(message, e);
        } else if (l < Level.WARNING.intValue()) {
            log.info(message, e);
        } else if (l < Level.SEVERE.intValue()) {
            log.warn(message, e);
        } else {
            log.error(message, e);
        }
    }
}
