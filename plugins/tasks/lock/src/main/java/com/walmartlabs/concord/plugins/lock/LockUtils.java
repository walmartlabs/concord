package com.walmartlabs.concord.plugins.lock;

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

import com.walmartlabs.concord.ApiClient;
import com.walmartlabs.concord.ApiException;
import com.walmartlabs.concord.client.ClientUtils;
import com.walmartlabs.concord.client.LockResult;
import com.walmartlabs.concord.client.ProcessLocksApi;
import com.walmartlabs.concord.sdk.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

public final class LockUtils {

    private static final Logger log = LoggerFactory.getLogger(LockUtils.class);

    private static final int RETRY_COUNT = 3;
    private static final long RETRY_INTERVAL = 5000;

    public static final String LOCK_NAME_KEY = "name";
    public static final String SCOPE_KEY = "scope";
    public static final String PROJECT_SCOPE = "PROJECT";

    static boolean lock(UUID instanceId, String lockName, String scope, ApiClient apiClient) throws ApiException {
        ProcessLocksApi api = new ProcessLocksApi(apiClient);

        log.info("Locking '{}' with scope '{}'...", lockName, scope);
        LockResult lock = withRetry(() -> api.tryLock(instanceId, lockName, scope));

        return lock.isAcquired();
    }

    static void unlock(UUID instanceId, String lockName, String scope, ApiClient apiClient) throws ApiException {
        ProcessLocksApi api = new ProcessLocksApi(apiClient);

        log.info("Unlocking '{}' with scope '{}'...", lockName, scope);
        withRetry(() -> {
            api.unlock(instanceId, lockName, scope);
            return null;
        });
    }

    static String getLockName(Map<String, Object> input) {
        String name = MapUtils.getString(input, LOCK_NAME_KEY);
        if (name == null) {
            if (input.containsKey(SCOPE_KEY)) {
                throw new IllegalArgumentException("`" + LOCK_NAME_KEY + "` is a mandatory field");
            } else {
                // short form - lock: lockName
                name = MapUtils.getString(input, "0");
            }
        }
        return name;
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
