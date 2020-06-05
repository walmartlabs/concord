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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.Callable;

import static com.walmartlabs.concord.plugins.lock.Constants.RETRY_COUNT;
import static com.walmartlabs.concord.plugins.lock.Constants.RETRY_INTERVAL;

public class LockTaskCommon {

    public interface Suspender {

        void suspend(String eventName);
    }

    private static final Logger log = LoggerFactory.getLogger(LockTaskCommon.class);

    private final ProcessLocksApi api;
    private final UUID instanceId;

    public LockTaskCommon(ApiClient apiClient, UUID instanceId) {
        this.api = new ProcessLocksApi(apiClient);
        this.instanceId = instanceId;
    }

    public boolean lock(String lockName, String lockScope, Suspender suspender) throws ApiException {
        log.info("Locking '{}' with scope '{}'...", lockName, lockScope);

        if (lockName == null) {
            throw new IllegalArgumentException("Mandatory variable 'lockName' is required");
        }

        LockResult lock = withRetry(() -> api.tryLock(instanceId, lockName, checkScope(lockScope)));

        boolean result = lock.isAcquired();
        if (!result) {
            suspender.suspend(lockName);
        }
        return result;
    }

    public void unlock(String lockName, String lockScope) throws ApiException {
        log.info("Unlocking '{}' with scope '{}'...", lockName, lockScope);

        withRetry(() -> {
            api.unlock(instanceId, lockName, checkScope(lockScope));
            return null;
        });
    }

    private String checkScope(String scope) {
        if (scope == null || scope.isEmpty()) {
            return null;
        }

        if (!scope.equalsIgnoreCase("ORG")
                && !scope.equalsIgnoreCase("PROJECT")) {

            throw new IllegalArgumentException("Unknown scope '" + scope + "', expected: org or project");
        }

        return scope.toUpperCase();
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
