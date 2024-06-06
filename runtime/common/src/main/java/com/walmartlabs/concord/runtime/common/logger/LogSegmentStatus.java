package com.walmartlabs.concord.runtime.common.logger;

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

public enum LogSegmentStatus {

    RUNNING (0),
    OK (1),
    SUSPENDED (2), // unused now...
    ERROR (3);

    private final int id;

    LogSegmentStatus(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static LogSegmentStatus fromString(String status) {
        for (LogSegmentStatus s : LogSegmentStatus.values()) {
            if (s.name().equals(status)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown log segment status: " + status);
    }
}
