package com.walmartlabs.concord.agent;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static boolean kill(Process proc) {
        return kill(proc.toHandle());
    }

    public static boolean kill(Process proc, boolean killDescendants) {
        List<ProcessHandle> children = killDescendants ? proc.children().toList() : List.of();

        // kill parent first which may gracefully clean up all descendents
        boolean killed = kill(proc.toHandle());

        // clean up orphaned processes that are still running
        children.stream()
                .flatMap(ProcessHandle::descendants)
                .forEach(Utils::kill);

        return killed;
    }

    private static boolean kill(ProcessHandle handle) {
        if (!handle.isAlive()) {
            return false;
        }

        String p = toString(handle);

        log.info("kill ['{}'] -> attempting to stop...", p);
        handle.destroy();

        if (handle.isAlive()) {
            sleep(1000);
        }

        while (handle.isAlive()) {
            log.warn("kill ['{}'] -> waiting for the process to die...", p);
            sleep(3000);
            handle.destroyForcibly();
        }

        return true;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String toString(ProcessHandle proc) {
        try {
            return "pid=" + proc.pid();
        } catch (UnsupportedOperationException e) {
            return proc.toString();
        }
    }

    private Utils() {
    }
}
