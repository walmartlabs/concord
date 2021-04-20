package com.walmartlabs.concord.server.process;

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

public enum ProcessDataInclude {

    CHECKPOINTS ("checkpoints"),
    CHECKPOINTS_HISTORY ("checkpointsHistory"),
    CHILDREN_IDS ("childrenIds"),
    STATUS_HISTORY ("history");

    private final String value;

    ProcessDataInclude(String value) {
        this.value = value;
    }

    public static ProcessDataInclude fromString(String str) {
        for (ProcessDataInclude v : values()) {
            if (v.value.equalsIgnoreCase(str)) {
                return v;
            }
        }
        throw new IllegalArgumentException(str + " not found");
    }
}
