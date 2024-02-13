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

import com.walmartlabs.concord.client2.*;
import com.walmartlabs.concord.runtime.v2.sdk.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

import static com.walmartlabs.concord.plugins.lock.Constants.RETRY_COUNT;
import static com.walmartlabs.concord.plugins.lock.Constants.RETRY_INTERVAL;

public class LockTaskCommon {

    private static final Logger log = LoggerFactory.getLogger(LockTaskCommon.class);

    private final ProcessApi processApi;
    private final ProcessLocksApi lockApi;
    private final UUID instanceId;

    public LockTaskCommon(ApiClient apiClient, UUID instanceId) {
        this.processApi = new ProcessApi(apiClient);
        this.lockApi = new ProcessLocksApi(apiClient);
        this.instanceId = instanceId;
    }

    public TaskResult lock(String lockName, String lockScope) throws ApiException {
        log.info("Locking '{}' with scope '{}'...", lockName, lockScope);

        if (lockName == null) {
            throw new IllegalArgumentException("Mandatory variable 'lockName' is required");
        }

        LockResult lock = withRetry(() -> lockApi.tryLock(instanceId, lockName, checkScope(lockScope)));

        boolean result = lock.getAcquired();
        if (!result) {
            withRetry(() -> {
                processApi.setWaitCondition(instanceId, createCondition(lock.getInfo()));
                return null;
            });

            return TaskResult.suspend(lockName);
        }
        return TaskResult.success();
    }

    public void unlock(String lockName, String lockScope) throws ApiException {
        log.info("Unlocking '{}' with scope '{}'...", lockName, lockScope);

        withRetry(() -> {
            lockApi.unlock(instanceId, lockName, checkScope(lockScope));
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

    /**
     *  @see com.walmartlabs.concord.server.process.waits.ProcessLockCondition
      */
    private static Map<String, Object> createCondition(LockEntry lock) {
        Map<String, Object> condition = new HashMap<>();
        condition.put("type", "PROCESS_LOCK");
        condition.put("instanceId", lock.getInstanceId());
        condition.put("orgId", lock.getOrgId());
        condition.put("projectId", lock.getProjectId());
        condition.put("scope", lock.getScope());
        condition.put("name", lock.getName());
        return condition;
    }

    private static <T> T withRetry(Callable<T> c) throws ApiException {
        return ClientUtils.withRetry(RETRY_COUNT, RETRY_INTERVAL, c);
    }
}
