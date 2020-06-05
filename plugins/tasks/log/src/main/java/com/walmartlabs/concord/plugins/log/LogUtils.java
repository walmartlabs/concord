package com.walmartlabs.concord.plugins.log;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtils {

    public static final Logger log = LoggerFactory.getLogger(LogUtils.class);

    private LogUtils() { }

    public static void error(String s) {
        log.error(s);
    }

    public static void error(Object o) {
        log.error("{}", o);
    }

    public static void warn(String s) {
        log.warn(s);
    }

    public static void warn(Object o) {
        log.warn("{}", o);
    }

    public static void debug(String s) {
        log.debug(s);
    }

    public static void debug(Object o) {
        log.debug("{}", o);
    }

    public static void info(String s) {
        log.info(s);
    }

    public static void info(Object o) {
        log.info("{}", o);
    }
}
