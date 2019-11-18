package com.walmartlabs.concord.plugins.slack;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.sdk.Context;

import java.util.Map;

public final class Utils {

    public static Integer getInteger(Map<String, Object> params, String name) {
        if (params == null) {
            return null;
        }
        return (Integer) params.get(name);
    }

    public static int getInteger(Map<String, Object> params, String name, int defaultValue) {
        Integer result = getInteger(params, name);
        if (result != null) {
            return result;
        }
        return defaultValue;
    }

    public static String getString(Map<String, Object> params, String name) {
        if (params == null) {
            return null;
        }
        return (String) params.get(name);
    }

    public static String getString(Context ctx, String k, String defaultValue) {
        Object v = ctx.getVariable(k);
        if (v == null) {
            return defaultValue;
        }

        if (!(v instanceof String)) {
            throw new IllegalArgumentException("Expected a '" + k + "' string, got " + v);
        }
        return (String) v;
    }

    public static Boolean getBoolean(Context ctx, String k, boolean defaultValue) {
        Object v = ctx.getVariable(k);

        if (v == null) {
            return defaultValue;
        }

        if(!(v instanceof Boolean)) {
            throw new IllegalArgumentException("Expected a boolean for '" + k + "', got " + v);
        }

        return (Boolean) v;
    }

    public static String assertString(Context ctx, String k) {
        String s = getString(ctx, k, null);
        if (s == null) {
            throw new IllegalArgumentException("Mandatory parameter '" + k + "' is required");
        }
        return s;
    }
}
