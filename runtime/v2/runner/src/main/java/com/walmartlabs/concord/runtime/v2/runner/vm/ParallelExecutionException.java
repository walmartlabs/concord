package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.runtime.model.Location;
import com.walmartlabs.concord.svm.ThreadError;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An exception that is thrown when multiple exceptions are thrown
 * in {@code parallel} blocks.
 */
public class ParallelExecutionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int MAX_STACK_TRACE_ELEMENTS = 3;
    private final List<ThreadError> exceptions;

    public ParallelExecutionException(Collection<ThreadError> causes) {
        super("Parallel execution errors: \n" + toMessage(causes));
        this.exceptions = new ArrayList<>(causes);
    }

    public List<Exception> getExceptions() {
        return exceptions.stream().map(ThreadError::exception).toList();
    }

    private static String toMessage(Collection<ThreadError> causes) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (ThreadError item : causes) {
            if (i > 0) {
                sb.append("\n\n");
            }
            sb.append("[").append(i + 1).append("] ");
            sb.append(stacktraceToString(item));
            i++;
        }
        return sb.toString();
    }

    @Override
    public void printStackTrace(PrintStream s) {
        s.println(getMessage());
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        s.println(getMessage());
    }

    private static String stacktraceToString(ThreadError e) {
        StringBuilder sb = new StringBuilder();
        sb.append(toErrorHeader(e));
        sb.append("\n  Error: ");
        sb.append(indentContinuationLines(e.exception().toString(), "  "));

        if (!e.callStack().isEmpty()) {
            sb.append("\n  Call stack:\n");
            sb.append(e.callStack().stream()
                    .map(item -> "    " + item)
                    .collect(Collectors.joining("\n")));
        }

        StackTraceElement[] elements = e.getStackTrace();
        if (elements.length > 0) {
            sb.append("\n  Java stack trace:\n");
        }

        int maxElements = Math.min(elements.length, MAX_STACK_TRACE_ELEMENTS);
        for (int i = 0; i < maxElements; i++) {
            StackTraceElement element = elements[i];
            sb.append("    at ").append(element.toString()).append("\n");
        }
        if (maxElements != elements.length) {
            sb.append("    ...").append(elements.length - maxElements).append(" more\n");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getMessage();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[0];
    }

    private static String toErrorHeader(ThreadError threadError) {
        String prefix = "";
        if (threadError.cmd() instanceof StepCommand<?> sc) {
            prefix = Location.toErrorPrefix(sc.getStep().getLocation()) + ", ";
        }
        return prefix + "thread: " + threadError.threadId().id();
    }

    private static String indentContinuationLines(String s, String indent) {
        return trimTrailingLineSeparators(s).lines().collect(Collectors.joining("\n" + indent));
    }

    private static String trimTrailingLineSeparators(String s) {
        int end = s.length();
        while (end > 0) {
            char c = s.charAt(end - 1);
            if (c != '\n' && c != '\r') {
                break;
            }
            end--;
        }
        return end == s.length() ? s : s.substring(0, end);
    }
}
