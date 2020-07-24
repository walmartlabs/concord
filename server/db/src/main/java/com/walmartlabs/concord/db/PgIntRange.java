package com.walmartlabs.concord.db;

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

import com.walmartlabs.concord.server.sdk.Range;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PgIntRange {

    private static final Pattern PATTERN = Pattern.compile("([\\[(])(\\d+),(\\d+)([])])");

    public static Range parse(String s) {
        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid range value: " + s);
        }

        Range.Mode lowerMode = parseMode(m.group(1));
        int lower = Integer.parseInt(m.group(2));
        int upper = Integer.parseInt(m.group(3));
        Range.Mode upperMode = parseMode(m.group(4));

        return Range.builder()
                .lowerMode(lowerMode)
                .lower(lower)
                .upper(upper)
                .upperMode(upperMode)
                .build();
    }

    private static Range.Mode parseMode(String s) {
        if ("[".equals(s) || "]".equals(s)) {
            return Range.Mode.INCLUSIVE;
        } else if ("(".equals(s) || ")".equals(s)) {
            return Range.Mode.EXCLUSIVE;
        } else {
            throw new IllegalArgumentException("Invalid range mode string: " + s);
        }
    }

    private PgIntRange() {
    }
}
