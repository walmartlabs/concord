package com.walmartlabs.concord.runtime.v2.parser;

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

import com.fasterxml.jackson.core.JsonLocation;

import java.io.File;

public final class JsonLocationConverter {

    public static String toShortString(JsonLocation location) {
        if (location == null) {
            return "n/a";
        }
        return "line: " + location.getLineNr() + ", col: " + location.getColumnNr();
    }

    public static String toFullString(JsonLocation location) {
        if (location == null) {
            return "n/a";
        }

        String src = "unknown";
        Object sourceRef = location.getSourceRef();
        if (sourceRef instanceof File) {
            src = ((File) sourceRef).getAbsolutePath();
        }

        return "src: [" + src + "] " + toShortString(location);
    }

    public JsonLocationConverter() {
    }
}
