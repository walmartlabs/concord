package com.walmartlabs.concord.agent;

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

import java.lang.reflect.Field;

public final class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static boolean kill(Process proc) {
        if (!proc.isAlive()) {
            return false;
        }

        String p = toString(proc);

        log.info("kill ['{}'] -> attempting to stop...", p);
        proc.destroy();

        if (proc.isAlive()) {
            sleep(1000);
        }

        while (proc.isAlive()) {
            log.warn("kill ['{}'] -> waiting for the process to die...", p);
            sleep(3000);
            proc.destroyForcibly();
        }

        return true;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String toString(Process proc) {
        try {
            Field f = proc.getClass().getDeclaredField("pid");
            f.setAccessible(true);

            return "pid=" + f.get(proc);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            return proc.toString();
        }
    }

    private Utils() {
    }
}
