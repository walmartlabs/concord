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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ContextUtils {

    public static UUID getUUID(Context ctx, String name) {
        Object o = ctx.getVariable(name);
        if (o == null) {
            return null;
        }
        if (o instanceof String) {
            return UUID.fromString((String) o);
        }
        if (o instanceof UUID) {
            return (UUID) o;
        }
        throw new IllegalArgumentException("Invalid variable '" + name + "' type, expected: string/uuid, got: " + o.getClass());
    }

    public static int getInt(Context ctx, String name, int defaultValue) {
        Integer result = getVariable(ctx, name, defaultValue, Integer.class);
        if (result == null) {
            return defaultValue;
        }
        return result;
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
        Boolean result = getVariable(ctx, name, defaultValue, Boolean.class);
        if (result == null) {
            return defaultValue;
        }
        return result;
    }

    public static <K, V> Map<K, V> getMap(Context ctx, String key) {
        return getMap(ctx, key, null);
    }

    public static <K, V> Map<K, V> getMap(Context ctx, HasKey k, Map<K, V> defaultValue) {
        return getMap(ctx, k.getKey(), defaultValue);
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

    public static String assertString(String message, Context ctx, String name) {
        return assertVariable(message, ctx, name, String.class);
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
        if (ctx == null) {
            return defaultValue;
        }

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
        return assertVariable(null, ctx, name, type);
    }

    public static <T> T assertVariable(String message, Context ctx, String name, Class<T> type) {
        T result = getVariable(ctx, name, null, type);

        if (result != null) {
            return result;
        }

        throw new IllegalArgumentException(message != null ? message : "Mandatory variable '" + name + "' is required");
    }

    public static ProjectInfo getProjectInfo(Context ctx) {
        Map<String, Object> pi = getMap(ctx, Constants.Request.PROJECT_INFO_KEY, null);
        if (pi == null) {
            return null;
        }

        UUID projectId = MapUtils.getUUID(pi, "projectId");
        if (projectId == null) {
            return null;
        }

        return ImmutableProjectInfo.builder()
                .orgId(MapUtils.assertUUID(pi, "orgId"))
                .orgName(MapUtils.getString(pi, "orgName"))
                .id(projectId)
                .name(MapUtils.getString(pi, "projectName"))
                .build();
    }

    public static RepositoryInfo getRepositoryInfo(Context ctx) {
        Map<String, Object> pi = getMap(ctx, Constants.Request.PROJECT_INFO_KEY, null);
        if (pi == null) {
            return null;
        }

        UUID repoId = MapUtils.getUUID(pi, "repoId");
        if (repoId == null) {
            return null;
        }

        return ImmutableRepositoryInfo.builder()
                .id(repoId)
                .name(MapUtils.getString(pi, "repoName"))
                .url(MapUtils.getString(pi, "repoUrl"))
                .build();
    }

    public static Path getWorkDir(Context ctx) {
        Object workDir = assertVariable(ctx, Constants.Context.WORK_DIR_KEY, Object.class);
        if (workDir instanceof Path) {
            return (Path) workDir;
        }
        if (workDir instanceof String) {
            return Paths.get((String) workDir);
        }
        throw new IllegalArgumentException("Invalid variable '" + Constants.Context.WORK_DIR_KEY + "' type, expected: string/path, got: " + workDir.getClass());
    }

    public static UUID getTxId(Context ctx) {
        Object txId = assertVariable(ctx, Constants.Context.TX_ID_KEY, Object.class);
        if (txId instanceof String) {
            return UUID.fromString((String) txId);
        }
        if (txId instanceof UUID) {
            return (UUID) txId;
        }
        throw new IllegalArgumentException("Invalid variable '" + Constants.Context.TX_ID_KEY + "' type, expected: string/uuid, got: " + txId.getClass());
    }

    public static String getSessionToken(Context ctx) {
        String result = sessionTokenOrNull(ctx);
        if (result == null) {
            throw new IllegalArgumentException("Session key not found in the process info: " + ctx.getVariable(Constants.Request.PROCESS_INFO_KEY));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static String sessionTokenOrNull(Context ctx) {
        Map<String, Object> processInfo = (Map<String, Object>) ctx.getVariable(Constants.Request.PROCESS_INFO_KEY);
        if (processInfo == null) {
            return null;
        }
        return (String) processInfo.get("sessionKey");
    }

    private ContextUtils() {
    }
}
