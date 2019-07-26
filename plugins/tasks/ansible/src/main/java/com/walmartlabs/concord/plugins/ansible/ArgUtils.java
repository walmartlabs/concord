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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ArgUtils {

    public static Path getPath(Map<String, Object> args, String key, Path workDir) {
        Object v = args.get(key);
        return getPath(key, v, workDir);
    }

    public static Path getPath(String key, Object o, Path workDir) {
        Path p = null;

        if (o instanceof String) {
            p = workDir.resolve((String) o);
        } else if (o instanceof Path) {
            p = workDir.resolve((Path) o);
        } else if (o != null) {
            throw new IllegalArgumentException("'" + key + "' should be either a relative path: " + o);
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

    public static String getListAsString(Map<String, Object> args, TaskParams c) {
        Object v = args.get(c.getKey());
        if (v == null) {
            return null;
        }

        if (v instanceof String) {
            return ((String) v).trim();
        }

        Collection<String> items = null;
        if (v instanceof Collection) {
            items = ((Collection<?>) v).stream().map(Object::toString).collect(Collectors.toList());
        } else if (v.getClass().isArray()) {
            items = Arrays.stream((Object[]) v).map(Object::toString).collect(Collectors.toList());
        }

        if (items != null) {
            return String.join(", ", items);
        }

        throw new IllegalArgumentException("Unexpected '" + c.getKey() + "' type: " + v);
    }

    private ArgUtils() {
    }
}
