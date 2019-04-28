package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.walmartlabs.concord.sdk.MapUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

public final class ArgUtils {

    public static Path getPath(Map<String, Object> args, String key, Path workDir) {
        Path p = null;

        Object v = args.get(key);
        if (v instanceof String) {
            p = workDir.resolve((String) v);
        } else if (v instanceof Path) {
            p = workDir.resolve((Path) v);
        } else if (v != null) {
            throw new IllegalArgumentException("'" + key + "' should be either a relative path: " + v);
        }

        if (p != null && !Files.exists(p)) {
            throw new IllegalArgumentException("File not found: " + workDir.relativize(p));
        }

        return p;
    }

    public static String assertString(String assertionMessage, Map<String, Object> args, String key) {
        String v = MapUtils.getString(args, key);
        if (v == null) {
            throw new IllegalArgumentException(assertionMessage);
        }
        return v;
    }

    @SuppressWarnings("unchecked")
    public static String getListAsString(Map<String, Object> args, TaskParams c) {
        Object v = args.get(c.getKey());
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return ((String) v).trim();
        }

        if (v instanceof Collection) {
            return String.join(", ", (Collection<String>) v);
        }

        throw new IllegalArgumentException("unexpected '" + c.getKey() + "' type: " + v);
    }

    private ArgUtils() {
    }
}
