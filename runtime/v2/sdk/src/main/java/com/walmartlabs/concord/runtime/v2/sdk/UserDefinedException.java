package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Doesn't produce a stack trace in process logs.
 */
public class UserDefinedException extends RuntimeException {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 8152584338845805365L;

    private final Map<String, Object> payload;

    public UserDefinedException(String message) {
        this(message, null);
    }

    public UserDefinedException(String message, Map<String, Object> payload) {
        super(message);
        this.payload = payload;
    }

    @Override
    public void printStackTrace(PrintStream s) {
        s.println(getMessage());
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        s.println(getMessage());
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}
