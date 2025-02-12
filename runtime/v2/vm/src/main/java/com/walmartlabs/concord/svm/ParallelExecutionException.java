package com.walmartlabs.concord.svm;

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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An exception that is thrown when multiple exceptions are thrown
 * in {@code parallel} blocks.
 */
public class ParallelExecutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_STACK_TRACE_ELEMENTS = 3;
    private final List<ThreadError> exceptions;

    public ParallelExecutionException(Collection<ThreadError> causes) {
        super("Parallel execution errors: \n" + toMessage(causes));
        this.exceptions = new ArrayList<>(causes);
    }

    public List<ThreadError> getExceptions() {
        return exceptions;
    }

    private static String toMessage(Collection<ThreadError> causes) {
        return causes.stream()
                .map(ParallelExecutionException::stacktraceToString)
                .collect(Collectors.joining("\n"));
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
        StringWriter sw = new StringWriter();
        sw.append(e.toString());

        StackTraceElement[] elements = e.getStackTrace();
        if (elements.length > 0) {
            sw.append("\n");
        }

        int maxElements = Math.min(elements.length, MAX_STACK_TRACE_ELEMENTS);
        for (int i = 0; i < maxElements; i++) {
            StackTraceElement element = elements[i];
            sw.append("\tat ").append(element.toString()).append("\n");
        }
        if (maxElements != elements.length) {
            sw.append("\t...").append(String.valueOf(elements.length - maxElements)).append(" more\n");
        }
        return sw.toString();
    }

    @Override
    public String toString() {
        return getMessage();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return new StackTraceElement[0];
    }
}
