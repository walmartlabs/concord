package com.walmartlabs.concord.runtime.v2.exception;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

public class InvalidValueException extends YamlProcessingException {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = -3923028761051516018L;

    private final List<String> expected;
    private final Serializable actual;

    private InvalidValueException(List<String> expected, Serializable actual, Location location) {
        super(location);
        this.expected = expected;
        this.actual = actual;
    }

    public List<String> getExpected() {
        return expected;
    }

    public Serializable getActual() {
        return actual;
    }

    @Override
    public String getMessage() {
        return buildMessage();
    }

    private String buildMessage() {
        String msg = "Invalid value: " + actual;

        StringJoiner expectedOptions = new StringJoiner(", ", "[", "]");
        expected.forEach(expectedOptions::add);
        msg += ", expected: " + expectedOptions.toString();

        return msg;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> expected;
        private Serializable actual;
        private Location location;

        public Builder expected(String ... expected) {
            return expected(Arrays.asList(expected));
        }

        public Builder expected(List<String> expected) {
            this.expected = expected;
            return this;
        }

        public Builder actual(Serializable actual) {
            this.actual = actual;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public InvalidValueException build() {
            return new InvalidValueException(expected, actual, location);
        }
    }
}
