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
import com.walmartlabs.concord.runtime.v2.parser.UnknownOption;

import java.util.List;
import java.util.StringJoiner;

public class UnknownOptionException extends YamlProcessingException {

    // for backward compatibility (java8 concord 1.92.0 version)
    private static final long serialVersionUID = 8029483558704928232L;

    private final List<String> expected;
    private final List<UnknownOption> unknown;

    private UnknownOptionException(List<String> expected, List<UnknownOption> unknown, Location location) {
        super(location);
        this.expected = expected;
        this.unknown = unknown;
    }

    public List<String> getExpected() {
        return expected;
    }

    public List<UnknownOption> getUnknown() {
        return unknown;
    }

    @Override
    public String getMessage() {
        return buildMessage();
    }

    private String buildMessage() {
        StringJoiner unknownOptions = new StringJoiner(", ", "[", "]");
        unknown.forEach(o -> unknownOptions.add(toString(o)));

        String msg = "Unknown options: " + unknownOptions;
        if (expected.isEmpty()) {
            msg += ", no options expected";
        } else {
            StringJoiner expectedOptions = new StringJoiner(", ", "[", "]");
            expected.forEach(expectedOptions::add);
            msg += ", expected: " + expectedOptions;
        }

        msg += ". Remove invalid options and/or fix indentation";

        return msg;
    }

    private String toString(UnknownOption o) {
        String type = "";
        if (o.type() != null) {
            type = " [" + o.type() + "]";
        }
        return "'" + o.key() + "'" + type + " @ " + Location.toShortString(o.location());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<String> expected;
        private List<UnknownOption> unknown;
        private Location location;

        public Builder from(UnknownOptionException e) {
            this.expected = e.getExpected();
            this.unknown = e.getUnknown();
            this.location = e.getLocation();
            return this;
        }

        public Builder expected(List<String> expected) {
            this.expected = expected;
            return this;
        }

        public Builder unknown(List<UnknownOption> unknown) {
            this.unknown = unknown;
            return this;
        }

        public Builder location(Location location) {
            this.location = location;
            return this;
        }

        public UnknownOptionException build() {
            return new UnknownOptionException(expected, unknown, location);
        }
    }
}
