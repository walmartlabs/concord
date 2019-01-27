package com.walmartlabs.concord.sdk;

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

import java.util.List;
import java.util.Map;

public final class ContextUtils {

    public static int getInt(Context ctx, String name, int defaultValue) {
        return getVariable(ctx, name, defaultValue, Integer.class);
    }

    public static Number getNumber(Context ctx, String name, Number defaultValue) {
        return getVariable(ctx, name, defaultValue, Number.class);
    }

    public static String getString(Context ctx, String name) {
        return getString(ctx, name, null);
    }

    public static String getString(Context ctx, String name, String defaultValue) {
        return getVariable(ctx, name, defaultValue, String.class);
    }

    public static boolean getBoolean(Context ctx, String name, boolean defaultValue) {
        return getVariable(ctx, name, defaultValue, Boolean.class);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> getMap(Context ctx, String name, Map<K, V> defaultValue) {
        return getVariable(ctx, name, defaultValue, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> getList(Context ctx, String name, List<E> defaultValue) {
        return getVariable(ctx, name, defaultValue, List.class);
    }

    public static int assertInt(Context ctx, String name) {
        return assertVariable(ctx, name, Integer.class);
    }

    public static Number assertNumber(Context ctx, String name) {
        return assertVariable(ctx, name, Number.class);
    }

    public static String assertString(Context ctx, String name) {
        return assertVariable(ctx, name, String.class);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> assertMap(Context ctx, String name) {
        return assertVariable(ctx, name, Map.class);
    }

    @SuppressWarnings("unchecked")
    public static <E> List<E> assertList(Context ctx, String name) {
        return assertVariable(ctx, name, List.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getVariable(Context ctx, String name, T defaultValue) {
        T result = (T) ctx.getVariable(name);

        if (result != null) {
            return result;
        }

        return defaultValue;
    }

    public static <T> T getVariable(Context ctx, String name, T defaultValue, Class<T> type) {
        Object value = getVariable(ctx, name, defaultValue);
        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: " + type + ", got: " + value.getClass());
    }

    public static <T> T assertVariable(Context ctx, String name, Class<T> type) {
        T result = getVariable(ctx, name, null, type);

        if (result != null) {
            return result;
        }

        throw new IllegalArgumentException("Mandatory variable '" + name + "' is required");
    }

    private ContextUtils() {
    }
}
