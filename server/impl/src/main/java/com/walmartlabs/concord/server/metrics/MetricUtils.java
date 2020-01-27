package com.walmartlabs.concord.server.metrics;

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

import com.codahale.metrics.Timer;

public class MetricUtils {

    private static final String SHARED_PREFIX = "com.walmartlabs.concord.";

    public static String createFqn(String type, Class<?> owner, String name, String suffix) {
        String n = owner.getName();
        if (n.startsWith(SHARED_PREFIX)) {
            n = n.substring(SHARED_PREFIX.length());
        }
        return type + "," + n + "." + name + (suffix != null ? suffix : "");
    }

    public static void withTimer(Timer timer, Runnable r) {
        Timer.Context t = timer.time();
        try {
            r.run();
        } finally {
            t.stop();
        }
    }
}
