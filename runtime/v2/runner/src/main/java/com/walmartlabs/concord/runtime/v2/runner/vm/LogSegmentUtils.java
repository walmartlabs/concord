package com.walmartlabs.concord.runtime.v2.runner.vm;

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

import com.walmartlabs.concord.runtime.v2.runner.logging.LogContext;
import com.walmartlabs.concord.svm.State;
import com.walmartlabs.concord.svm.ThreadId;

import java.util.ArrayList;
import java.util.List;

public final class LogSegmentUtils {

    private static final String KEY = "logContext";

    public static LogContext popLogContext(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("Can't pop log context: empty log context");
        }

        LogContext result = items.remove(items.size() - 1);
        if (items.isEmpty()) {
            state.removeThreadLocal(threadId, KEY);
        }
        return result;
    }

    public static LogContext getLogContext(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return null;
        }

        if (items.isEmpty()) {
            throw new RuntimeException("Can't get log context: empty log context");
        }

        return items.get(items.size() - 1);
    }

    public static List<LogContext> getLogContexts(ThreadId threadId, State state) {
        List<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            return List.of();
        }

        return items;
    }

    public static void pushLogContext(ThreadId threadId, State state, LogContext logContext) {
        ArrayList<LogContext> items = state.getThreadLocal(threadId, KEY);
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(logContext);
        state.setThreadLocal(threadId, KEY, items);
    }

    private LogSegmentUtils() {
    }
}