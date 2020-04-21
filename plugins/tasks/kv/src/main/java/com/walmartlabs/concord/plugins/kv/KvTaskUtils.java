package com.walmartlabs.concord.plugins.kv;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2020 Walmart Inc.
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

import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.ProcessKvStoreApi;

import java.util.UUID;

public final class KvTaskUtils {

    public static void remove(ProcessKvStoreApi api, UUID txId, String key) throws Exception {
        assertValidKey(key);
        ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () -> {
            api.removeKey(txId, key);
            return null;
        });
    }

    public static void putString(ProcessKvStoreApi api, UUID txId, String key, String value) throws Exception {
        assertValidKey(key);
        ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () -> {
            api.putString(txId, key, value);
            return null;
        });
    }

    public static String getString(ProcessKvStoreApi api, UUID txId, String key) throws Exception {
        assertValidKey(key);
        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.getString(txId, key));
    }

    public static void putLong(ProcessKvStoreApi api, UUID txId, String key, Long value) throws Exception {
        assertValidKey(key);
        ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () -> {
            api.putLong(txId, key, value);
            return null;
        });
    }

    public static Long getLong(ProcessKvStoreApi api, UUID txId, String key) throws Exception {
        assertValidKey(key);
        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.getLong(txId, key));
    }

    public static long incLong(ProcessKvStoreApi api, UUID txId, String key) throws Exception {
        assertValidKey(key);
        return ClientUtils.withRetry(Constants.RETRY_COUNT, Constants.RETRY_INTERVAL, () ->
                api.incLong(txId, key));
    }

    private static void assertValidKey(String s) {
        if (s == null || s.isEmpty()) {
            throw new IllegalArgumentException("Keys cannot be empty or null");
        }
    }

    private KvTaskUtils() {
    }
}
