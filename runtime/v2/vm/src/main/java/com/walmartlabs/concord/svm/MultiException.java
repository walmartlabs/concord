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
import java.util.Collection;
import java.util.stream.Collectors;

public class MultiException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private static final int MAX_STACK_TRACE_ELEMENTS = 3;

    public MultiException(Collection<Exception> causes) {
        super("Parallel execution errors: \n" + toMessage(causes));
    }

    private static String toMessage(Collection<Exception> causes) {
        return causes.stream()
                .map(MultiException::stacktraceToString)
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

    private static String stacktraceToString(Exception e) {
        StringWriter sw = new StringWriter();
        sw.append(e.toString()).append("\n");
        StackTraceElement[] elements = e.getStackTrace();
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
}
