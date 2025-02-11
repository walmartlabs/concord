package com.walmartlabs.concord.runtime.v2.runner.vm;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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
import java.util.Objects;

public class WrappedException extends RuntimeException {

    private final String prefix;

    public WrappedException(Exception cause) {
        this(null, Objects.requireNonNull(cause));
    }

    public WrappedException(String prefix, Exception cause) {
        super(Objects.requireNonNull(cause));
        this.prefix = prefix;
    }

    @Override
    public Exception getCause() {
        return (Exception) super.getCause();
    }

    @Override
    public StackTraceElement[] getStackTrace() {
        return getCause().getStackTrace();
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        getCause().printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintStream s) {
        getCause().printStackTrace(s);
    }

    @Override
    public String getLocalizedMessage() {
        return messagePrefix() + getCause().getLocalizedMessage();
    }

    @Override
    public String getMessage() {
        return messagePrefix() + getCause().getMessage();
    }

    @Override
    public String toString() {
        return messagePrefix() + getCause().toString();
    }

    private String messagePrefix() {
        if (prefix != null) {
            return prefix;
        }
        return "";
    }
}
