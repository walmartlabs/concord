package com.walmartlabs.concord.runtime.v2.runner.logging;

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

public final class LogUtils {

    /**
     * The segment ID lookup's maximum depth (limits nesting of {@link ThreadGroup}s).
     */
    private static final int MAX_DEPTH = 100;

    public static Long getSegmentId() {
        LogContext ctx = getContext();
        if (ctx == null) {
            return null;
        }
        return ctx.segmentId();
    }

    public static boolean isDuplicateToSystem() {
        LogContext ctx = getContext();
        if (ctx == null) {
            return false;
        }
        return ctx.duplicateToSystemSegment();
    }

    public static LogContext getContext() {
        int depth = 0;

        ThreadGroup g = Thread.currentThread().getThreadGroup();
        while (true) {
            if (g instanceof LogContextThreadGroup) {
                LogContextThreadGroup ttg = (LogContextThreadGroup) g;
                return ttg.getContext();
            }

            if (g.getParent() == null) {
                break;
            }

            g = g.getParent();
            depth++;

            if (depth >= MAX_DEPTH) {
                throw new IllegalStateException("Maximum ThreadGroup nesting limit is reached. " +
                        "This is most likely a bug in the runtime and/or a plugin.");
            }
        }

        return null;
    }

    private LogUtils() {
    }
}
